package net.msrandom.minecraftcodev.forge.task

import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.msrandom.minecraftcodev.core.resolve.MinecraftDownloadVariant
import net.msrandom.minecraftcodev.core.resolve.MinecraftVersionMetadata
import net.msrandom.minecraftcodev.core.resolve.downloadMinecraftFile
import net.msrandom.minecraftcodev.core.utils.walk
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import kotlin.io.path.bufferedReader
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.sequences.filter

internal object DistAttributePostProcessor {
    const val NEOFORGE_DISTS_ATTRIBUTE_NAME = "Minecraft-Dists"

    fun postProcess(
        outputPath: Path,
        clientJar: Path,
        serverJar: Path,
        cacheDirectory: Path,
        metadata: MinecraftVersionMetadata,
        isOffline: Boolean,
    ) {
        zipFileSystem(outputPath).use { fs ->
            val clientMappings = downloadMinecraftFile(
                cacheDirectory,
                metadata,
                MinecraftDownloadVariant.ClientMappings,
                isOffline
            )?.let {
                val tree = MemoryMappingTree()

                ProGuardFileReader.read(it.bufferedReader(), tree)

                tree
            }

            val from = clientMappings?.getNamespaceId(MappingUtil.NS_TARGET_FALLBACK) ?: -1
            val to = clientMappings?.getNamespaceId(MappingUtil.NS_SOURCE_FALLBACK) ?: -1

            val manifestPath = fs.getPath(JarFile.MANIFEST_NAME)
            val manifest = manifestPath.inputStream().use(::Manifest)

            manifest.mainAttributes.putValue(NEOFORGE_DISTS_ATTRIBUTE_NAME, "client server")

            zipFileSystem(clientJar).use { clientFs ->
                zipFileSystem(serverJar).use { serverFs ->
                    val root = clientFs.getPath("/")

                    root.walk {
                        val clientEntries = filter(Path::isRegularFile).mapNotNull {
                            val name = it.relativeTo(root).toString()

                            val path = if (name.endsWith(".class")) {
                                val className = name.substring(0, name.length - ".class".length)
                                val mappedName = clientMappings?.getClass(className, from)?.getName(to) ?: className

                                "$mappedName.class"
                            } else {
                                name
                            }

                            if (serverFs.getPath(path).notExists()) {
                                null
                            } else {
                                path
                            }
                        }

                        for (path in clientEntries) {
                            val attributes = Attributes().apply {
                                putValue("Minecraft-Dist", "client")
                            }

                            manifest.entries.put(path, attributes)
                        }
                    }
                }
            }

            manifestPath.outputStream().use(manifest::write)
        }
    }
}
