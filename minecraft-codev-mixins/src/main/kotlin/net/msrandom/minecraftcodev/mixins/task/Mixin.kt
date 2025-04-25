package net.msrandom.minecraftcodev.mixins.task

import com.google.common.base.Joiner
import com.google.common.collect.HashMultimap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.msrandom.minecraftcodev.core.utils.SetMultimapSerializer
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.mixins.mixin.GradleMixinService
import net.msrandom.minecraftcodev.mixins.mixin.MappingIoRemapperAdapter
import net.msrandom.minecraftcodev.mixins.mixinListingRules
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.service.MixinService
import java.nio.file.FileVisitResult
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.fileVisitor
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.visitFileTree
import kotlin.io.path.writeBytes

@CacheableTask
abstract class Mixin : DefaultTask() {
    companion object {
        private val JOINER = Joiner.on('.')
    }

    abstract val inputFile: RegularFileProperty
        @InputFile
        @Classpath
        get

    abstract val mixinFiles: ConfigurableFileCollection
        @InputFiles
        @Classpath
        get

    abstract val classpath: ConfigurableFileCollection
        @InputFiles
        @CompileClasspath
        get

    abstract val side: Property<Side>
        @Input get

    abstract val mappings: RegularFileProperty
        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        get

    abstract val sourceNamespace: Property<String>
        @Input get

    abstract val targetNamespace: Property<String>
        @Input get

    abstract val outputFile: RegularFileProperty
        @OutputFile get

    abstract val appliedMixins: RegularFileProperty
        @OutputFile get

    init {
        outputFile.convention(
            project.layout.file(
                inputFile.map {
                    temporaryDir.resolve("${it.asFile.nameWithoutExtension}-with-mixins.${it.asFile.extension}")
                },
            ),
        )

        appliedMixins.convention(
            project.layout.file(
                inputFile.map {
                    temporaryDir.resolve("${it.asFile.nameWithoutExtension}-applied-mixins.json")
                },
            ),
        )

        side.convention(Side.UNKNOWN)
    }

    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    @TaskAction
    fun mixin() {
        val input = inputFile.getAsPath()
        val output = outputFile.getAsPath()

        output.deleteIfExists()

        MixinEnvironment.getDefaultEnvironment().setOption(
            MixinEnvironment.Option.REFMAP_REMAP,
            System.getProperty("mixin.env.remapRefMap", "true").toBoolean()
        )

        (MixinService.getService() as GradleMixinService).use(
            classpath + mixinFiles + project.files(input),
            side.get()
        ) {
            val mappings = MemoryMappingTree()

            Tiny2FileReader.read(this@Mixin.mappings.asFile.get().reader(), mappings)

            MixinEnvironment.getDefaultEnvironment().remappers.add(
                MappingIoRemapperAdapter(
                    mappings,
                    sourceNamespace.get(),
                    targetNamespace.get()
                )
            )

            val appliedMixins = HashMultimap.create<String, String>()
            recorderExtension.appliedMixins = appliedMixins

            CLASSPATH@ for (mixinFile in mixinFiles + project.files(input)) {
                zipFileSystem(mixinFile.toPath()).use fs@{
                    val root = it.getPath("/")

                    val handler =
                        mixinListingRules.firstNotNullOfOrNull { rule ->
                            rule.load(root)
                        }

                    if (handler == null) {
                        return@fs
                    }

                    Mixins.addConfigurations(
                        handler.list(root).toTypedArray(),
                        ContainerHandleURI(mixinFile.toPath().toUri())
                    )
                }
            }

            zipFileSystem(input).use { inputFs ->
                val root = inputFs.getPath("/")

                zipFileSystem(output, true).use { outputFs ->
                    root.visitFileTree(fileVisitor {
                        onVisitFile { path, attr ->
                            val outputPath = outputFs.getPath(
                                path.getName(0).name,
                                *path.drop(1).map { it.name }.toList().toTypedArray()
                            )

                            outputPath.createParentDirectories()

                            if (path.extension == "class") {
                                val pathName = JOINER.join(root.relativize(path))

                                val name = pathName.substring(0, pathName.length - ".class".length)

                                outputPath.writeBytes(transformer.transformClassBytes(name, name, path.readBytes()))
                            } else {
                                path.copyTo(
                                    outputPath,
                                    StandardCopyOption.COPY_ATTRIBUTES,
                                    StandardCopyOption.REPLACE_EXISTING
                                )
                            }
                            return@onVisitFile FileVisitResult.CONTINUE
                        }
                    })
                }
            }

            Json.encodeToStream(
                SetMultimapSerializer(String.serializer(), String.serializer()),
                appliedMixins,
                this@Mixin.appliedMixins.asFile.get().outputStream()
            )
        }
    }
}
