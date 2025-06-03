package net.msrandom.minecraftcodev.fabric.task

import kotlinx.serialization.json.*
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.includes.IncludedJarInfo
import net.msrandom.minecraftcodev.includes.IncludesJar
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.language.base.plugins.LifecycleBasePlugin
import kotlin.io.path.*

abstract class JarInJar : IncludesJar() {
    abstract val outputDirectory: DirectoryProperty
        @Internal get

    init {
        group = LifecycleBasePlugin.BUILD_GROUP

        outputDirectory.convention(project.layout.dir(project.provider { temporaryDir.resolve("includes") }))

        from(project.zipTree(input))

        from(outputDirectory) {
            it.into("META-INF/jars")
        }

        doFirst(::addIncludedJarMetadata)
        doLast(::processModJson)
    }

    private fun addIncludedJarMetadata(@Suppress("unused") task: Task) {
        val includes = includeArtifacts.get()

        if (includes.isEmpty()) {
            return
        }

        val info = IncludedJarInfo.fromResolutionResults(includesRootComponent.get(), includeArtifacts.get(), logger)

        val dir = outputDirectory.getAsPath().createDirectories()

        dir.forEachDirectoryEntry { it.deleteIfExists() }

        info.forEach {
            val copy = it.file.toPath().copyTo(dir.resolve(it.file.name), true)

            zipFileSystem(copy).use { fs ->
                val metadataPath = fs.getPath(MinecraftCodevFabricPlugin.MOD_JSON)

                if (metadataPath.exists()) {
                    return@forEach
                }

                val group = it.group.replace('.', '_')
                val name = it.moduleName
                val version = it.artifactVersion

                val metadata = buildJsonObject {
                    put("schemaVersion", 1)
                    put("id", "${group}_$name")
                    put("version", version)
                    put("name", name)

                    putJsonObject("custom") {
                        put("fabric-loom:generated", true)
                    }
                }

                metadataPath.createFile().outputStream().use {
                    json.encodeToStream(metadata, it)
                }
            }
        }
    }

    fun processModJson(@Suppress("unused") task: Task) {
        if (outputDirectory.get().asFileTree.isEmpty) {
            return
        }

        zipFileSystem(archiveFile.get().toPath()).use {
            val input = it.getPath(MinecraftCodevFabricPlugin.MOD_JSON)

            if (!input.exists()) {
                throw IllegalStateException("Cannot process nonexistent mod json")
            }

            val inputJson = input.inputStream().use {
                json.decodeFromStream<JsonObject>(it)
            }

            val updatedMetadata = buildJsonObject {
                inputJson.forEach { (key, value) -> put(key, value) }

                putJsonArray("jars") {
                    for (file in outputDirectory.get().asFileTree) {
                        addJsonObject {
                            put("file", "META-INF/jars/${file.name}")
                        }
                    }
                }
            }

            input.outputStream().use {
                json.encodeToStream(updatedMetadata, it)
            }
        }
    }
}
