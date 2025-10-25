package net.msrandom.minecraftcodev.runs

import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import kotlin.io.path.readText

interface DependencyModOutputListing {
    val modIdFile: RegularFileProperty
        @InputFile get

    val outputs: ConfigurableFileCollection
        @InputFiles get
}

@JvmDefaultWithoutCompatibility
interface OutputListings {
    val modId: Property<String>
        @Input get

    val outputs: ConfigurableFileCollection
        @InputFiles get

    val dependencies: ListProperty<DependencyModOutputListing>
        @Nested get

    fun flatten(): Map<String, ConfigurableFileCollection> {
        return buildMap {
            for (dependency in dependencies.get()) {
                put(dependency.modIdFile.getAsPath().readText(), dependency.outputs)
            }

            put(modId.get(), outputs)
        }
    }
}
