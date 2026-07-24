package net.msrandom.minecraftcodev.remapper

import net.msrandom.minecraftcodev.core.utils.cacheExpensiveOperation
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
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
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createTempDirectory
import kotlin.io.path.copyTo
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

const val REMAP_OPERATION_VERSION = 4

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

        abstract val nonModFiles: ConfigurableFileCollection
            @PathSensitive(PathSensitivity.NONE)
            @InputFiles
            get

        abstract val cacheDirectory: DirectoryProperty
            @Internal get

        @get:Internal
        abstract val mappingService: Property<MappingService>

        abstract val remapperId: Property<String>
            @Input get

        init {
            apply {
                targetNamespace.convention(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

                filterMods.convention(true)
            }
        }
    }

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

        if (parameters.filterMods.get() && (input in parameters.nonModFiles || (input !in parameters.modFiles && !isMod(input.toPath())))) {
            outputs.file(inputFile)

            return
        }

        val sourceNamespace = parameters.sourceNamespace.get()
        val targetNamespace = parameters.targetNamespace.get()

        val output = outputs.file("${input.nameWithoutExtension}-$targetNamespace.${input.extension}")

        val classpath = (classpath + parameters.modFiles + parameters.extraClasspath) - input

        val inputPath = input.toPath()

        val mappingsPath = parameters.mappings.getAsPath()

        val cacheKey = buildList<Path> {
            add(mappingsPath)
            addAll(classpath.map { it.toPath() })
            add(inputPath)
        }
        val cacheOperationName = "remap-$REMAP_OPERATION_VERSION-$sourceNamespace-$targetNamespace"

        cacheExpensiveOperation(parameters.cacheDirectory.getAsPath(), cacheOperationName, cacheKey, output.toPath()) { (output) ->
            println("Remapping mod $input from $sourceNamespace to $targetNamespace")

            val service = parameters.mappingService.get()

            service.remap(
                remapperId = parameters.remapperId.get(),
                input = inputPath,
                output = output,
                mappingsPath = mappingsPath,
                classpathFiles = classpath,
                sourceNamespace = sourceNamespace,
                targetNamespace = targetNamespace,
            ).join()

            zipFileSystem(output).use { outputFs ->
                val root = outputFs.getPath("/")
                val handler = includedJarListingRules.firstNotNullOfOrNull { it.load(root) }
                if (handler != null) {
                    for (includedJar in handler.list(root)) {
                        val path = outputFs.getPath(includedJar)

                        val cacheKey = buildList<Path> {
                            add(mappingsPath)
                            addAll(classpath.map { it.toPath() })
                            add(path)
                        }

                        cacheExpensiveOperation(
                            parameters.cacheDirectory.getAsPath(),
                            cacheOperationName,
                            cacheKey,
                            path,
                        ) { (output) ->
                            println("Remapping ${input.name} nested jar $includedJar from $sourceNamespace to $targetNamespace")

                            val input = createTempDirectory("nested-jar-remap").resolve("${path.nameWithoutExtension}-$targetNamespace.${path.extension}")

                            path.copyTo(input, StandardCopyOption.REPLACE_EXISTING)

                            val service = parameters.mappingService.get()

                            service.remap(
                                remapperId = parameters.remapperId.get(),
                                input = input,
                                output = output,
                                mappingsPath = mappingsPath,
                                classpathFiles = classpath,
                                sourceNamespace = sourceNamespace,
                                targetNamespace = targetNamespace,
                            ).join()
                        }
                    }
                }
            }
        }
    }
}
