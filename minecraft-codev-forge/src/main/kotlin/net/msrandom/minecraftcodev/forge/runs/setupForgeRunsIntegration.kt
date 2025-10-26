package net.msrandom.minecraftcodev.forge.runs

import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.runs.MinecraftCodevRunsPlugin
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType

internal fun Project.setupForgeRunsIntegration() {
    plugins.withType<MinecraftCodevRunsPlugin<*>>() {
        val defaults = extension<RunsContainer>().extension<RunConfigurationDefaultsContainer>()

        defaults.extensions.create("forge", ForgeRunsDefaultsContainer::class, defaults)
    }
}
