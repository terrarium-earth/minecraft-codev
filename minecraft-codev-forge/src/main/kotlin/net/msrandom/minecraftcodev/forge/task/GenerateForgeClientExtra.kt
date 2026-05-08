package net.msrandom.minecraftcodev.forge.task

import net.msrandom.minecraftcodev.core.resolve.downloadFullMinecraftClient
import net.msrandom.minecraftcodev.core.task.CachedMinecraftTask
import net.msrandom.minecraftcodev.core.task.MinecraftVersioned
import net.msrandom.minecraftcodev.core.task.versionList
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.walk
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import kotlin.io.path.copyTo
import kotlin.io.path.deleteExisting
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.sequences.filter

abstract class GenerateForgeClientExtra : CachedMinecraftTask(), MinecraftVersioned {
    abstract val outputFile: RegularFileProperty
        @OutputFile get

    abstract val neoforge: Property<Boolean>
        @Input get

    init {
        outputFile.convention(
            project.layout.file(
                minecraftVersion.map {
                    temporaryDir.resolve("minecraft-$it-patched-client-extra.jar")
                },
            ),
        )
    }

    @TaskAction
    fun generate() {
        val cacheDirectory = cacheParameters.directory.getAsPath()
        val isOffline = cacheParameters.getIsOffline().get()
        val metadata = cacheParameters.versionList().version(minecraftVersion.get())

        val outputFile = outputFile.asFile.get().toPath()

        val clientJar = downloadFullMinecraftClient(cacheDirectory, metadata, isOffline)

        clientJar.copyTo(outputFile, StandardCopyOption.REPLACE_EXISTING)

        zipFileSystem(outputFile).use { clientFs ->
            clientFs.getPath("/").walk {
                for (path in filter(Path::isRegularFile)) {
                    if (path.toString().endsWith(".class") || path.startsWith("/META-INF")) {
                        path.deleteExisting()
                    }
                }
            }

            if (neoforge.get()) {
                val manifest = Manifest().apply {
                    mainAttributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0")
                    mainAttributes.putValue(DistAttributePostProcessor.NEOFORGE_DISTS_ATTRIBUTE_NAME, "client")
                }

                clientFs.getPath(JarFile.MANIFEST_NAME).outputStream().use(manifest::write)
            }
        }
    }
}
