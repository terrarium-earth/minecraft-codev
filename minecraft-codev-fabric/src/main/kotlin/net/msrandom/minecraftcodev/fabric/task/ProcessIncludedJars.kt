package net.msrandom.minecraftcodev.fabric.task

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.isComponentFromDependency
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.*

private val JSON: Json = Json { prettyPrint = true }

abstract class ProcessIncludedJars : DefaultTask() {

    abstract val includeConfiguration: Property<Configuration>
        @Internal get

    abstract val outputDirectory: DirectoryProperty
        @Internal get

    init {
        outputDirectory.convention(project.layout.dir(project.provider(::getTemporaryDir)))
    }

    @TaskAction
    fun processIncludedJars() {
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
                    put("custom", buildJsonObject {
                        put("fabric-loom:generated", true)
                    })
                }

                fabricmodjson.createFile().outputStream().use {
                    JSON.encodeToStream(json, it)
                }
            }
        }
    }
}