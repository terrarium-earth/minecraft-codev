package net.msrandom.minecraftcodev.runs.task

import kotlinx.serialization.json.encodeToStream
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.runs.ModOutputs
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.outputStream

@CacheableTask
abstract class GenerateModOutputs : DefaultTask() {
    abstract val modId: Property<String>
        @Input get

    abstract val paths: ListProperty<String>
        @Input
        get

    abstract val output: RegularFileProperty
        @OutputFile get

    init {
        output.convention(project.layout.file(project.provider { temporaryDir.resolve("outputs.json") }))
    }

    @TaskAction
    fun generate() {
        val outputs = ModOutputs(modId.get(), paths.get())

        output.getAsPath().outputStream().use {
            json.encodeToStream(outputs, it)
        }
    }
}
