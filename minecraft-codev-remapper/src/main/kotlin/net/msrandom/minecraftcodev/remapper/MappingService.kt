package net.msrandom.minecraftcodev.remapper

import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.remapper.extra.InnerClassRemapper
import net.msrandom.minecraftcodev.remapper.extra.SimpleFallbackRemapper
import net.msrandom.minecraftcodev.remapper.extra.kotlin.KotlinMetadataRemappingClassVisitor
import net.msrandom.minecraftcodev.remapper.task.loadCachedMappingFile
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.lang.AutoCloseable
import java.nio.file.Path
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Predicate
import java.util.jar.JarFile
import java.util.jar.Manifest
import kotlin.concurrent.withLock
import kotlin.io.path.exists
import kotlin.io.path.inputStream

private const val FABRIC_MIXIN_REMAP_TYPE = "Fabric-Loom-Mixin-Remap-Type"

data class Remapper(
    val mappings: MappingTreeView,
    val sourceNamespace: String,
    val targetNamespace: String,
    val tinyRemapper: TinyRemapper,
    val staticMixinRemapTags: MutableSet<InputTag>,
    val lock: ReentrantLock,
) {
    companion object {
        fun build(
            mappings: MappingTreeView,
            sourceNamespace: String,
            targetNamespace: String,
        ): Remapper {
            val sourceNamespaceId = mappings.getNamespaceId(sourceNamespace)
            val targetNamespaceId = mappings.getNamespaceId(targetNamespace)

            val builder = TinyRemapper
                .newRemapper()
                .keepInputData(true)
                .ignoreFieldDesc(true)
                .renameInvalidLocals(true)
                .rebuildSourceFilenames(true)
                .extension {
                    it.extraPreApplyVisitor { cls, next ->
                        KotlinMetadataRemappingClassVisitor(cls.environment.remapper, next)
                    }
                }
                .extraRemapper(
                    InnerClassRemapper(
                        mappings,
                        sourceNamespaceId,
                        targetNamespaceId,
                    ),
                )
                .extraRemapper(
                    SimpleFallbackRemapper(
                        mappings,
                        sourceNamespaceId,
                        targetNamespaceId,
                    )
                )
                .withMappings(mappingProvider(mappings, sourceNamespace, targetNamespace))

            val mixinExtensionPredicate = object : Predicate<InputTag> {
                val staticMixinRemapTags = mutableSetOf<InputTag>()

                override fun test(t: InputTag) = t in staticMixinRemapTags
            }

            val mixinExtension = MixinExtension(
                EnumSet.allOf(MixinExtension.AnnotationTarget::class.java),
                mixinExtensionPredicate,
            )

            val remapper = builder.extension(mixinExtension).build()

            return Remapper(
                mappings,
                sourceNamespace,
                targetNamespace,
                remapper,
                mixinExtensionPredicate.staticMixinRemapTags,
                ReentrantLock(),
            )
        }
    }
}

abstract class MappingService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private val remappers = ConcurrentHashMap<String, Remapper>()

    fun remap(
        remapperId: String,
        input: Path,
        output: Path,
        mappingsPath: Path,
        classpathFiles: Iterable<File>,
        sourceNamespace: String,
        targetNamespace: String,
    ): CompletableFuture<Void> {
        val fullRemapper = remappers.computeIfAbsent(remapperId) {
            val mappings = loadCachedMappingFile(mappingsPath)
            Remapper.build(mappings, sourceNamespace, targetNamespace)
        }

        fullRemapper.lock.withLock {
            val remapper = fullRemapper.tinyRemapper
            val mappings = fullRemapper.mappings

            OutputConsumerPath.Builder(output).build().use { outputConsumerPath ->
                val classpathPaths = classpathFiles.map(File::toPath).filter {
                    // TODO This is not a good way of detecting directories
                    '.' in it.last().toString() || it.exists()
                }

                val staticRemap = zipFileSystem(input).use {
                    val manifestFile = it.getPath(JarFile.MANIFEST_NAME)

                    if (manifestFile.exists()) {
                        val manifest = manifestFile.inputStream().use(::Manifest)

                        manifest.mainAttributes.getValue(FABRIC_MIXIN_REMAP_TYPE) == "static"
                    } else {
                        false
                    }
                }

                outputConsumerPath.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper)

                val inputTag = remapper.createInputTag()

                if (staticRemap) {
                    fullRemapper.staticMixinRemapTags += inputTag
                }

                try {
                    CompletableFuture.allOf(
                        remapper.readClassPathAsync(*classpathPaths.toTypedArray()),
                        remapper.readInputsAsync(inputTag, input),
                    ).thenRun {
                        remapper.apply(outputConsumerPath, inputTag)

                        zipFileSystem(output).use { fs ->
                            remapFiles(remapper, mappings, fs, sourceNamespace, targetNamespace)
                        }
                    }.join()
                } finally {
                    if (staticRemap) {
                        fullRemapper.staticMixinRemapTags -= inputTag
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(null)
    }

    override fun close() {
        for ((_, value) in remappers) {
            value.lock.withLock {
                value.tinyRemapper.finish()
            }
        }
    }
}
