package net.msrandom.minecraftcodev.runs.task

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.bufferedWriter
import kotlin.io.path.outputStream

/**
 * Writes a simple run configuration file that can be easily read by run-config-wrapper
 */
@CacheableTask
abstract class PrepareRun : DefaultTask() {
    abstract val mainClass: Property<String>
        @Input
        get

    abstract val arguments: ListProperty<String>
        @Input
        get

    abstract val properties: MapProperty<String, String>
        @Input
        get

    abstract val environment: MapProperty<String, String>
        @Input
        get

    abstract val configFile: RegularFileProperty
        @OutputFile get

    abstract val envFile: RegularFileProperty
        @OutputFile get

    init {
        val runConfigs = project.layout.buildDirectory.dir("run-configs")

        configFile.convention(runConfigs.map { it.file("${name}.cfg") })
        envFile.convention(runConfigs.map { it.file("${name}.env") })
    }

    @TaskAction
    fun writeConfig() {
        val config = buildJsonObject {
            put("main", JsonPrimitive(mainClass.get()))

            putJsonArray("arguments") {
                for (argument in arguments.get()) {
                    add(argument)
                }
            }

            putJsonObject("properties") {
                for ((key, value) in properties.get()) {
                    put(key, value)
                }
            }
        }

        configFile.getAsPath().outputStream().use {
            json.encodeToStream(config, it)
        }

        envFile.getAsPath().bufferedWriter().use {
            for ((key, value) in environment.get()) {
                it.appendLine("$key=$value")
            }
        }
    }
}
