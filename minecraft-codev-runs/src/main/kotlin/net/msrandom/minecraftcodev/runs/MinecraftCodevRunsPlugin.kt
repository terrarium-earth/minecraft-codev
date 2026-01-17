package net.msrandom.minecraftcodev.runs

import net.msrandom.minecraftcodev.core.utils.applyPlugin
import net.msrandom.minecraftcodev.core.utils.createSourceSetElements
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectoryProvider
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import net.msrandom.minecraftcodev.runs.task.PrepareRun
import org.gradle.api.Plugin
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.PluginAware
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.util.internal.GUtil
import kotlin.io.path.createDirectories

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

            runs.all {
                val configuration = this

                configuration.prepareTask.configure {
                    dependsOn(configuration.beforeRun)
                }

                configuration.runTask.configure {
                    configFile.set(configuration.prepareTask.flatMap(PrepareRun::configFile))
                    envFile.set(configuration.prepareTask.flatMap(PrepareRun::envFile))

                    javaLauncher.set(project.serviceOf<JavaToolchainService>().launcherFor {
                        languageVersion.set(configuration.jvmVersion.map(JavaLanguageVersion::of))
                    })

                    jvmArgumentProviders.add(configuration.jvmArguments::get)

                    workingDir(configuration.workingDirectory)

                    mainClass.set(RUN_WRAPPER_MAIN)

                    classpath = files(configuration.sourceSet.map(SourceSet::getRuntimeClasspath))

                    group = ApplicationPlugin.APPLICATION_GROUP

                    dependsOn(
                        configuration.dependsOn.map {
                            it.map(MinecraftRunConfiguration::runTask)
                        },
                    )

                    dependsOn(configuration.sourceSet.map(SourceSet::getClassesTaskName))
                }
            }
        }

    companion object {
        internal const val RUN_WRAPPER_MAIN = "earth.terrarium.cloche.runwrapper.Main"
    }
}
