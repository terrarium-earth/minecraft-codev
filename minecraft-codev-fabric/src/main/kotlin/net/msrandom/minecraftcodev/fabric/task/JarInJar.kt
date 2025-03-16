package net.msrandom.minecraftcodev.fabric.task

import kotlinx.serialization.json.*
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.isComponentFromDependency
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import kotlin.io.path.*

private val JSON: Json = Json { prettyPrint = true }

abstract class JarInJar : Jar() {
    abstract val includeConfiguration: Property<Configuration>
        @Internal get

    abstract val input: RegularFileProperty
        @InputFile get

    abstract val workingDirectory: DirectoryProperty
        @Internal get

    init {
        group = LifecycleBasePlugin.BUILD_GROUP

        from(project.zipTree(input))

        doFirst { generateFabricModJsons() }

        from(workingDirectory) {
            it.into("META-INF/jars")
        }
    }

    private fun generateFabricModJsons() {
        val includes = includeConfiguration.get()
        val dependencies = includes.incoming.resolutionResult.allComponents.map(ResolvedComponentResult::getId).associateWith { componentId ->
            val dependency = includes.allDependencies.firstOrNull { dependency ->
                isComponentFromDependency(componentId, dependency)
            }

            dependency
        }

        val dir = workingDirectory.getAsPath().createDirectories()

        includes.incoming.artifacts.forEach {
            val dependency = dependencies[it.id.componentIdentifier] ?: return@forEach
            val copy = it.file.toPath().copyTo(dir.resolve(it.file.name), true)

            zipFileSystem(copy).use { fs ->
                val fabricmodjson = fs.getPath("fabric.mod.json")
                if (fabricmodjson.exists()) return@forEach

                val json = buildJsonObject {
                    put("schemaVersion", 1)
                    put("id", dependency.group?.let { "${it.replace('.', '_')}_${dependency.name}" } ?: dependency.name)
                    put("version", dependency.version ?: "0.0.0")
                    put("name", dependency.name)
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
