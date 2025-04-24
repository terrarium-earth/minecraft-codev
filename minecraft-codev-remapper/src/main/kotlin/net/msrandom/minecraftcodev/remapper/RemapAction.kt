package net.msrandom.minecraftcodev.remapper

import net.fabricmc.mappingio.format.tiny.Tiny2FileReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.msrandom.minecraftcodev.core.utils.cacheExpensiveOperation
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.includes.includedJarListingRules
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.copyTo
import kotlin.io.path.reader

@CacheableTransform
abstract class RemapAction : TransformAction<RemapAction.Parameters> {
    abstract class Parameters : TransformParameters {
        abstract val mappings: RegularFileProperty
            @InputFile
            @PathSensitive(PathSensitivity.NONE)
            get

        abstract val sourceNamespace: Property<String>
            @Input get

        abstract val targetNamespace: Property<String>
            @Input get

        abstract val extraClasspath: ConfigurableFileCollection
            @CompileClasspath
            @InputFiles
            get

        abstract val filterMods: Property<Boolean>
            @Input get

        abstract val modFiles: ConfigurableFileCollection
            @PathSensitive(PathSensitivity.NONE)
            @InputFiles
            get

        abstract val cacheDirectory: DirectoryProperty
            @Internal get

        init {
            apply {
                targetNamespace.convention(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

                filterMods.convention(true)
            }
        }
    }

    abstract val objectFactory: ObjectFactory
        @Inject get

    abstract val inputFile: Provider<FileSystemLocation>
        @InputArtifact
        @PathSensitive(PathSensitivity.NONE)
        get

    abstract val classpath: FileCollection
        @Classpath
        @InputArtifactDependencies
        get

    override fun transform(outputs: TransformOutputs) {
        val input = inputFile.get().asFile

        if (parameters.filterMods.get() && input !in parameters.modFiles && !isMod(input.toPath())) {
            outputs.file(inputFile)

            return
        }

        val sourceNamespace = parameters.sourceNamespace.get()
        val targetNamespace = parameters.targetNamespace.get()

        val output = outputs.file("${input.nameWithoutExtension}-$targetNamespace.${input.extension}")

        val classpath = (classpath + parameters.modFiles + parameters.extraClasspath) - input

        val inputPath = input.toPath()

        val cacheKey = buildList<Path> {
            add(parameters.mappings.getAsPath())
            add(inputPath)
        }

        cacheExpensiveOperation(parameters.cacheDirectory.getAsPath(), "remap-$REMAP_OPERATION_VERSION", cacheKey, output.toPath()) { (output) ->
            println("Remapping mod $input from $sourceNamespace to $targetNamespace")

            val mappings = MemoryMappingTree()

            Tiny2FileReader.read(parameters.mappings.getAsPath().reader(), mappings)

            JarRemapper.remap(
                mappings,
                sourceNamespace,
                targetNamespace,
                inputPath,
                output,
                classpath,
            )

            FileSystems.newFileSystem(inputPath, null).use { inputFS ->
                val root = inputFS.getPath("/")
                val handler = includedJarListingRules.firstNotNullOfOrNull { it.load(root) }
                if (handler != null) {
                    for (includedJar in handler.list(root)) {
                        val path = inputFS.getPath(includedJar)

                        val cacheKey = buildList<Path> {
                            add(parameters.mappings.getAsPath())
                            add(path)
                        }

                        cacheExpensiveOperation(
                            parameters.cacheDirectory.getAsPath(),
                            "remap-$REMAP_OPERATION_VERSION",
                            cacheKey,
                            path,
                        ) { (output) ->
                            println("Remapping ${input.name} nested jar $includedJar from $sourceNamespace to $targetNamespace")

                            val input = Files.createTempFile(path.nameWithoutExtension, "remap-input.${path.extension}")

                            path.copyTo(input, StandardCopyOption.REPLACE_EXISTING)

                            JarRemapper.remap(
                                mappings,
                                sourceNamespace,
                                targetNamespace,
                                input,
                                output,
                                classpath,
                            )
                        }
                    }
                }
            }
        }
    }
}
