package net.msrandom.minecraftcodev.fabric.runs

import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.runs.MinecraftCodevRunsPlugin
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType

fun Project.setupFabricRunsIntegration() {
    plugins.withType<MinecraftCodevRunsPlugin<*>> {
        val defaults = extension<RunsContainer>().extension<RunConfigurationDefaultsContainer>()

        defaults.extensions.create("fabric", FabricRunsDefaultsContainer::class, defaults)
    }
}
