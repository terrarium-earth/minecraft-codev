package net.msrandom.minecraftcodev.includes

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.jvm.tasks.Jar

abstract class IncludesJar : Jar() {
    abstract val includedJarInfo: ListProperty<IncludedJarInfo>
        @Nested
        get

    abstract val input: RegularFileProperty
        @InputFile
        get

    fun fromResolutionResults(configuration: Provider<Configuration>) {
        val objects = project.objects
        val logger = logger

        includedJarInfo.addAll(configuration.flatMap {
            it.incoming.resolutionResult.rootComponent.zip(it.incoming.artifacts.resolvedArtifacts) { rootComponent, resolvedArtifacts ->
                IncludedJarInfo.fromResolutionResults(
                    objects,
                    rootComponent,
                    resolvedArtifacts,
                    logger,
                )
            }
        })
    }
}
