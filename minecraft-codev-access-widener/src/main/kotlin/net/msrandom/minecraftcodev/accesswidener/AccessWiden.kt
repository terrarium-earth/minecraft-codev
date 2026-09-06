package net.msrandom.minecraftcodev.accesswidener

import net.msrandom.minecraftcodev.core.utils.cacheExpensiveOperation
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.getLocalCacheDirectoryProvider
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.core.utils.tryLink
import net.msrandom.minecraftcodev.core.utils.walk
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeBytes

const val ACCESS_WIDEN_OPERATION_VERSION = 1

@CacheableTask
abstract class AccessWiden : DefaultTask() {
    abstract val inputFile: RegularFileProperty
        @InputFile
        @Classpath
        get

    abstract val accessWideners: ConfigurableFileCollection
        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        get

    abstract val namespace: Property<String>
        @Optional
        @Input
        get

    abstract val namedSource: Property<Boolean>
        @Optional
        @Input
        get

    abstract val outputFile: RegularFileProperty
        @OutputFile get

    abstract val cacheDirectory: DirectoryProperty
        @Internal get

    init {
        outputFile.convention(
            project.layout.file(
                inputFile.map {
                    temporaryDir.resolve("${it.asFile.nameWithoutExtension}-access-widened.${it.asFile.extension}")
                },
            ),
        )

        cacheDirectory.set(getLocalCacheDirectoryProvider(project))
    }

    @TaskAction
    fun accessWiden() {
        val cacheKey = buildList<Path> {
            addAll(accessWideners.map { it.toPath() })
            add(inputFile.getAsPath())
        }

        cacheExpensiveOperation(
            cacheDirectory.getAsPath(),
            "access-widen-$ACCESS_WIDEN_OPERATION_VERSION",
            cacheKey,
            outputFile.getAsPath(),
        ) { (output) ->
            accessWiden(output, inputFile.getAsPath(), accessWideners, namespace, namedSource)
        }
    }
}

abstract class AccessWidenerAction : TransformAction<AccessWidenerAction.Parameters> {
    abstract class Parameters : TransformParameters {
        abstract val accessWideners: ConfigurableFileCollection
            @PathSensitive(PathSensitivity.NONE)
            @InputFiles
            get

        abstract val namespace: Property<String>
            @Optional
            @Input
            get

        abstract val namedSource: Property<Boolean>
            @Optional
            @Input
            get

        abstract val filterMods: Property<Boolean>
            @Optional
            @Input get

        abstract val nonModFiles: ConfigurableFileCollection
            @PathSensitive(PathSensitivity.NONE)
            @InputFiles
            get

        abstract val cacheDirectory: DirectoryProperty
            @Internal get
        init {
            apply {
                namespace.convention("named")
                filterMods.convention(true)
            }
        }
    }

    abstract val inputFile: Provider<FileSystemLocation>
        @InputArtifact
        @PathSensitive(PathSensitivity.NONE)
        get

    override fun transform(outputs: TransformOutputs) {
        val input = inputFile.get().asFile

        if (parameters.accessWideners.isEmpty) {
            outputs.file(inputFile)
            return
        }

        val accessModifiers = loadAccessWideners( //TODO reuse accessModifiers across files
            parameters.accessWideners,
            parameters.namespace.takeIf(Property<*>::isPresent)?.get(),
            parameters.namedSource.getOrElse(false),
        )

        val inputPath = inputFile.get().toPath()
        var isAccessWiden = false

        zipFileSystem(inputPath).let { zInput ->
            isAccessWiden = accessModifiers.classTree.findFirst(zInput.getPath("/"))
        }

        if (!isAccessWiden) {
            outputs.file(inputFile)
            return
        }

        val output = outputs.file("${input.nameWithoutExtension}-AW.${input.extension}")

        val cacheKey = buildList<Path> {
            addAll(parameters.accessWideners.map { it.toPath() })
            add(inputPath)
        }

        cacheExpensiveOperation(
            parameters.cacheDirectory.getAsPath(),
            "access-widen-mod-$ACCESS_WIDEN_OPERATION_VERSION",
            cacheKey,
            output.toPath(),
        ) { (op) ->
            accessWiden(op, inputPath, accessModifiers, parameters.namespace, parameters.namedSource)
        }
    }
}

fun accessWiden(outputPath: Path, inputPath: Path, accessWideners: FileCollection,
                namespace: Property<String>, namedSource: Property<Boolean>) {
    if (accessWideners.isEmpty) {
        outputPath.tryLink(inputPath)
        return
    }

    val accessModifiers = loadAccessWideners(
        accessWideners,
        namespace.takeIf(Property<*>::isPresent)?.get(),
        namedSource.getOrElse(false),
    )

    accessWiden(outputPath, inputPath, accessModifiers, namespace, namedSource)
}

fun accessWiden(outputPath: Path, inputPath: Path, accessModifiers: AccessModifiers,
                namespace: Property<String>, namedSource: Property<Boolean>) {
    zipFileSystem(inputPath).use { inputZip ->
        zipFileSystem(outputPath, true).use { outputZip ->
            inputZip.getPath("/").walk {
                for (path in filter(Path::isRegularFile)) {
                    val name = path.toString()
                    val zipOutputPath = outputZip.getPath(name)
                    zipOutputPath.parent?.createDirectories()

                    if (!name.endsWith(".class")) {
                        path.copyTo(zipOutputPath, StandardCopyOption.COPY_ATTRIBUTES)
                        continue
                    }

                    val className = name.substring(1, name.length - ".class".length)

                    if (!accessModifiers.canModifyAccess(className)) {
                        path.copyTo(zipOutputPath, StandardCopyOption.COPY_ATTRIBUTES)
                        continue
                    }

                    val reader = path.inputStream().use(::ClassReader)
                    val writer = ClassWriter(0)

                    reader.accept(AccessModifierClassVisitor(Opcodes.ASM9, writer, accessModifiers), 0)

                    zipOutputPath.writeBytes(writer.toByteArray())
                }
            }
        }
    }
}