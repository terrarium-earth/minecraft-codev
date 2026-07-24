package net.msrandom.minecraftcodev.runs

import net.msrandom.minecraftcodev.core.utils.applyPlugin
import net.msrandom.minecraftcodev.core.utils.createSourceSetElements
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectoryProvider
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Plugin
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class MinecraftCodevRunsPlugin<T : PluginAware> : Plugin<T> {
    override fun apply(target: T) =
        applyPlugin(target) {
            // Log4j configs
            val cache = getGlobalCacheDirectoryProvider(this)
            // val logging: Path = cache.resolve("logging")

            fun addSourceElements(
                extractNativesTaskName: String,
                downloadAssetsTaskName: String,
            ) {
                tasks.register<ExtractNatives>(extractNativesTaskName) {
                    group = ApplicationPlugin.APPLICATION_GROUP
                }

                tasks.register<DownloadAssets>(downloadAssetsTaskName) {
                    group = ApplicationPlugin.APPLICATION_GROUP
                }
            }

            createSourceSetElements {
                addSourceElements(
                    it.extractNativesTaskName,
                    it.downloadAssetsTaskName,
                )
            }

            val runs = project.extensions.create(RunsContainer::class, "minecraftRuns", RunsContainerImpl::class, cache)

            runs.extensions.create("defaults", RunConfigurationDefaultsContainer::class)

            project.integrateIdeaRuns()
        }
}
