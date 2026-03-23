package net.msrandom.minecraftcodev.includes

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.bundling.Jar

abstract class IncludesJar : Jar() {
    abstract val includedJarInfo: ListProperty<IncludedJarInfo>
        @Nested
        get

    abstract val input: RegularFileProperty
        @InputFile
        get

    fun fromResolutionResults(configuration: Provider<out Configuration>) {
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
