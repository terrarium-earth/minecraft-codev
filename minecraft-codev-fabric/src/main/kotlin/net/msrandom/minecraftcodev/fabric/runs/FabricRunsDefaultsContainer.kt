package net.msrandom.minecraftcodev.fabric.runs

import kotlinx.serialization.json.decodeFromStream
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.resolve.MinecraftVersionMetadata
import net.msrandom.minecraftcodev.core.task.versionList
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.fabric.FabricInstaller
import net.msrandom.minecraftcodev.fabric.loadFabricInstaller
import net.msrandom.minecraftcodev.runs.DatagenRunConfigurationData
import net.msrandom.minecraftcodev.runs.ModOutputs
import net.msrandom.minecraftcodev.runs.RunConfigurationData
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Action
import java.io.File
import kotlin.io.path.createDirectories

open class FabricRunsDefaultsContainer(private val defaults: RunConfigurationDefaultsContainer) {
    private fun defaults(data: RunConfigurationData, sidedMain: FabricInstaller.MainClass.() -> String) {
        // TODO Make into a property
        if (SystemUtils.IS_OS_MAC_OSX) {
            defaults.configuration.jvmArguments("-XstartOnFirstThread")
        }

        defaults.configuration.jvmArguments("-Dfabric.development=true")
        defaults.configuration.jvmArguments("-Dmixin.env.remapRefMap=true")

        defaults.configuration.apply {
            val remapClasspathDirectory = project.layout.buildDirectory.dir("fabricRemapClasspath")

            val modClasses = project.provider {
                val allOutputs = data.modOutputs.map {
                    val outputs = it.inputStream().use {
                        val outputs = json.decodeFromStream<ModOutputs>(it)

                        outputs.paths
                    }

                    compileArguments(outputs).map {
                        it.joinToString(File.pathSeparator)
                    }
                }

                compileArguments(allOutputs)
            }.flatMap {
                it.map { it.joinToString(File.pathSeparator + File.pathSeparator) }
            }

            jvmArguments.add(compileArgument("-Dfabric.classPathGroups=", modClasses))

            mainClass.set(
                sourceSet.map {
                    val fabricInstaller = loadFabricInstaller(it.runtimeClasspath, false)!!

                    fabricInstaller.mainClass.sidedMain()
                },
            )

            jvmArguments.add(
                sourceSet.zip(remapClasspathDirectory) { sourceSet, directory ->
                    // TODO Make into a task
                    val file = directory.file("classpath.txt")
                    val runtimeClasspath = sourceSet.runtimeClasspath

                    file.toPath().parent.createDirectories()
                    file.asFile.writeText(runtimeClasspath.files.joinToString("\n", transform = File::getAbsolutePath))

                    compileArgument("-Dfabric.remapClasspathFile=", file.asFile)
                }.flatMap { it }
            )
        }
    }

    fun client(action: Action<RunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(RunConfigurationData::class.java)

        action.execute(data)

        client(data)
    }

    private fun addAssets(data: RunConfigurationData) {
        defaults.configuration.apply {
            val assetIndex =
                data.minecraftVersion.map {
                    cacheParameters
                        .versionList()
                        .version(it)
                        .assetIndex
                }

            val assetsDirectory = data.downloadAssetsTask.flatMap(DownloadAssets::assetsDirectory)

            arguments.add(compileArgument("--assetsDir=", assetsDirectory))
            arguments.add(compileArgument("--assetIndex=", assetIndex.map(MinecraftVersionMetadata.AssetIndex::id)))

            beforeRun.add(data.downloadAssetsTask)
        }
    }

    private fun client(data: RunConfigurationData) {
        defaults(data, FabricInstaller.MainClass::client)
        addAssets(data)

        defaults.configuration.apply {
            val nativesDirectory = data.extractNativesTask.flatMap(ExtractNatives::destinationDirectory)

            jvmArguments.add(compileArgument("-Djava.library.path=", nativesDirectory))
            jvmArguments.add(compileArgument("-Dorg.lwjgl.librarypath=", nativesDirectory))

            beforeRun.add(data.extractNativesTask)
        }
    }

    private fun server(data: RunConfigurationData) {
        defaults(data, FabricInstaller.MainClass::server)

        defaults.configuration.arguments("nogui")
    }

    fun server(action: Action<RunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(RunConfigurationData::class.java)

        action.execute(data)

        server(data)
    }

    fun data(action: Action<DatagenRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(DatagenRunConfigurationData::class.java)

        action.execute(data)

        defaults(data, FabricInstaller.MainClass::server)
        data(data)
    }

    fun clientData(action: Action<DatagenRunConfigurationData>) {
        // Fabric doesn't have a dedicated client data entrypoint or properties
        data(action)
    }

    private fun data(data: DatagenRunConfigurationData) {
        addAssets(data)

        defaults.configuration.apply {
            jvmArguments.add("-Dfabric-api.datagen")
            jvmArguments.add(compileArgument("-Dfabric-api.datagen.output-dir=", data.outputDirectory))
            jvmArguments.add(compileArgument("-Dfabric-api.datagen.modid=", data.modId))
        }
    }

    private fun gameTest(data: RunConfigurationData) {
        defaults.configuration.jvmArguments(
            "-Dfabric-api.gametest",
            "-Dfabric.autoTest",
        )
    }

    fun gameTestServer(action: Action<RunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(RunConfigurationData::class.java)
        action.execute(data)

        server(data)
        gameTest(data)
    }

    fun gameTestClient(action: Action<RunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(RunConfigurationData::class.java)
        action.execute(data)

        client(data)
        gameTest(data)
    }
}
