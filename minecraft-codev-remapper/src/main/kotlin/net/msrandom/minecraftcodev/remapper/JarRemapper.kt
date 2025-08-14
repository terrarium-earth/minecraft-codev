package net.msrandom.minecraftcodev.remapper

import net.msrandom.minecraftcodev.remapper.extra.kotlin.KotlinMetadataRemappingClassVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.remapper.extra.InnerClassRemapper
import net.msrandom.minecraftcodev.remapper.extra.SimpleFallbackRemapper
import java.io.File
import java.nio.file.Path
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import kotlin.io.path.listDirectoryEntries

const val REMAP_OPERATION_VERSION = 3

private fun hasRefmaps(path: Path) = zipFileSystem(path).use {
    it.getPath("/").listDirectoryEntries("*refmap.json").isNotEmpty()
}

object JarRemapper {
    @Synchronized
    fun remap(
        mappings: MappingTreeView,
        sourceNamespace: String,
        targetNamespace: String,
        input: Path,
        output: Path,
        classpath: Iterable<File>,
    ) {
        val sourceNamespaceId = mappings.getNamespaceId(sourceNamespace)
        val targetNamespaceId = mappings.getNamespaceId(targetNamespace)
        val builder = TinyRemapper
            .newRemapper()
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

        val mixinExtensionTargets = if (hasRefmaps(input)) {
            EnumSet.of(MixinExtension.AnnotationTarget.HARD)
        } else {
            EnumSet.allOf(MixinExtension.AnnotationTarget::class.java)
        }

        val remapper = builder.extension(MixinExtension(mixinExtensionTargets)).build()

        try {
            OutputConsumerPath.Builder(output).build().use {
                it.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper)

                CompletableFuture.allOf(
                    remapper.readClassPathAsync(*classpath.map(File::toPath).toTypedArray()),
                    remapper.readInputsAsync(input),
                ).join()

                remapper.apply(it)
            }

            zipFileSystem(output).use { fs ->
                remapFiles(remapper, mappings, fs, sourceNamespace, targetNamespace)
            }
        } finally {
            remapper.finish()
        }
    }
}
