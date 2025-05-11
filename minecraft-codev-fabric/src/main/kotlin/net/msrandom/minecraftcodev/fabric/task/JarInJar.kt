package net.msrandom.minecraftcodev.fabric.task

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.isComponentFromDependency
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import kotlin.io.path.*

private val JSON = Json { prettyPrint = true }

abstract class JarInJar : Jar() {
    abstract val includeConfiguration: Property<Configuration>
        @Internal get

    abstract val outputDirectory: DirectoryProperty
        @Internal get

    abstract val input: RegularFileProperty
        @InputFile get

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
        val includes = includeConfiguration.get()
        val dependencies = includes.incoming.resolutionResult.allComponents.map(ResolvedComponentResult::getId).associateWith { componentId ->
            val dependency = includes.allDependencies.firstOrNull { dependency ->
                isComponentFromDependency(componentId, dependency)
            }

            dependency
        }

        val dir = outputDirectory.getAsPath().createDirectories()

        dir.forEachDirectoryEntry { it.deleteIfExists() }

        includes.incoming.artifacts.forEach {
            val dependency = dependencies[it.id.componentIdentifier] ?: return@forEach
            val module = it.id.componentIdentifier as? ModuleComponentIdentifier
            val copy = it.file.toPath().copyTo(dir.resolve(it.file.name), true)

            zipFileSystem(copy).use { fs ->
                val fabricmodjson = fs.getPath(MinecraftCodevFabricPlugin.MOD_JSON)
                if (fabricmodjson.exists()) return@forEach

                val group = (module?.group ?: dependency.group)?.replace('.', '_')
                val name = module?.module ?: dependency.name
                val version = module?.version ?: dependency.version ?: "0.0.0"

                val json = buildJsonObject {
                    put("schemaVersion", 1)
                    put("id", group?.let { "${group}_$name" } ?: name)
                    put("version", version)
                    put("name", name)

                    putJsonObject("custom") {
                        put("fabric-loom:generated", true)
                    }
                }

                fabricmodjson.createFile().outputStream().use {
                    JSON.encodeToStream(json, it)
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
                JSON.decodeFromStream<JsonObject>(it)
            }

            val json = buildJsonObject {
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
                JSON.encodeToStream(json, it)
            }
        }
    }
}
