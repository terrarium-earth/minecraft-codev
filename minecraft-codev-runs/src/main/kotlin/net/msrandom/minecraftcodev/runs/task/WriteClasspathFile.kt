package net.msrandom.minecraftcodev.runs.task

import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.bufferedWriter

@CacheableTask
abstract class WriteClasspathFile : DefaultTask() {
    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val classpath: ConfigurableFileCollection
        @InputFiles
        @PathSensitive(PathSensitivity.ABSOLUTE)
        get

    abstract val separator: Property<String>
        @Optional
        @Input
        get

    init {
        output.set(temporaryDir.resolve("classpath.txt"))
        separator.convention("\n")
    }

    @TaskAction
    fun generate() {
        output.getAsPath().bufferedWriter().use { writer ->
            val iterator = classpath.iterator()

            if (!iterator.hasNext()) {
                return
            }

            do {
                writer.write(iterator.next().absolutePath)

                if (iterator.hasNext()) {
                    writer.write(separator.get())
                } else {
                    break
                }
            } while (true)
        }
    }
}
