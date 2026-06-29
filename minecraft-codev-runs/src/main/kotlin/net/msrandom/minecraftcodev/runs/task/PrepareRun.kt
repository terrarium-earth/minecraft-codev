package net.msrandom.minecraftcodev.runs.task

import kotlinx.serialization.json.encodeToStream
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.runs.SerializedRunConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.outputStream

@CacheableTask
abstract class PrepareRun : DefaultTask() {
    abstract val arguments: ListProperty<String>
        @Input
        get

    abstract val jvmArguments: ListProperty<String>
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

    @TaskAction
    fun writeConfig() {
        val config = SerializedRunConfig(
            arguments.get(),
            jvmArguments.get(),
            properties.get(),
            environment.get(),
        )

        configFile.getAsPath().outputStream().use {
            json.encodeToStream(config, it)
        }
    }
}
