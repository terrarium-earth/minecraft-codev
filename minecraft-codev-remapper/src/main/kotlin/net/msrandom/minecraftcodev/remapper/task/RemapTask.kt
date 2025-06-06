package net.msrandom.minecraftcodev.remapper.task

import net.fabricmc.mappingio.format.tiny.Tiny2FileReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.msrandom.minecraftcodev.core.utils.cacheExpensiveOperation
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectoryProvider
import net.msrandom.minecraftcodev.remapper.JarRemapper
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.REMAP_OPERATION_VERSION
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path
import javax.inject.Inject

@CacheableTask
abstract class RemapTask : DefaultTask() {
    abstract val inputFile: RegularFileProperty
        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        get

    abstract val sourceNamespace: Property<String>
        @Input get

    abstract val targetNamespace: Property<String>
        @Optional
        @Input
        get

    abstract val mappings: RegularFileProperty
        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        get

    abstract val classpath: ConfigurableFileCollection
        @InputFiles
        @CompileClasspath
        get

    abstract val cacheDirectory: DirectoryProperty
        @Internal get

    abstract val outputFile: RegularFileProperty
        @OutputFile get

    abstract val objectFactory: ObjectFactory
        @Inject get

    init {
        run {
            outputFile.convention(
                project.layout.file(
                    inputFile.flatMap { file ->
                        targetNamespace.map { namespace ->
                            temporaryDir.resolve("${file.asFile.nameWithoutExtension}-$namespace.${file.asFile.extension}")
                        }
                    },
                ),
            )

            targetNamespace.convention(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

            cacheDirectory.set(getGlobalCacheDirectoryProvider(project))
        }
    }

    @TaskAction
    fun remap() {
        val cacheKey = buildList<Path> {
            addAll(classpath.map { it.toPath() })
            add(mappings.getAsPath())
            add(inputFile.getAsPath())
        }

        cacheExpensiveOperation(cacheDirectory.getAsPath(), "remap-$REMAP_OPERATION_VERSION", cacheKey, outputFile.getAsPath()) { (output) ->
            val mappings = MemoryMappingTree()

            Tiny2FileReader.read(this.mappings.asFile.get().reader(), mappings)

            JarRemapper.remap(
                mappings,
                sourceNamespace.get(),
                targetNamespace.get(),
                inputFile.getAsPath(),
                output,
                classpath,
            )
        }
    }
}
