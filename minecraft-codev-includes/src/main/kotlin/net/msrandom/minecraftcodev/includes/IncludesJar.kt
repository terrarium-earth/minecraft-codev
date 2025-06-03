package net.msrandom.minecraftcodev.includes

import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.jvm.tasks.Jar

abstract class IncludesJar : Jar() {
    abstract val includesRootComponent: Property<ResolvedComponentResult>
        @Internal
        get

    abstract val includeArtifacts: SetProperty<ResolvedArtifactResult>
        @Internal
        get

    abstract val input: RegularFileProperty
        @InputFile
        get
}
