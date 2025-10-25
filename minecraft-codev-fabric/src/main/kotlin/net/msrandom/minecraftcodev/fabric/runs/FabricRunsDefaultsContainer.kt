package net.msrandom.minecraftcodev.fabric.runs

import net.msrandom.minecraftcodev.core.resolve.MinecraftVersionMetadata
import net.msrandom.minecraftcodev.core.task.versionList
import net.msrandom.minecraftcodev.fabric.FabricInstaller
import net.msrandom.minecraftcodev.fabric.loadFabricInstaller
import net.msrandom.minecraftcodev.runs.DatagenRunConfigurationData
import net.msrandom.minecraftcodev.runs.RunConfigurationData
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Action
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.io.File
import kotlin.io.path.readText

open class FabricRunsDefaultsContainer(private val defaults: RunConfigurationDefaultsContainer) {
    private fun defaults(data: FabricRunConfigurationData, sidedMain: FabricInstaller.MainClass.() -> String) {
        // TODO Make into a property
        if (SystemUtils.IS_OS_MAC_OSX) {
            defaults.configuration.jvmArguments("-XstartOnFirstThread")
        }

        defaults.configuration.jvmArguments("-Dfabric.development=true")
        defaults.configuration.jvmArguments("-Dmixin.env.remapRefMap=true")
        defaults.configuration.beforeRun(data.writeRemapClasspathTask)

        defaults.configuration.apply {
            val modClasses = project.provider {
                val byModId = data.modOutputs.get().flatten()

                val allOutputs = byModId.map { (_, files) ->
                    compileArguments(files).map {
                        it.joinToString(File.pathSeparator)
                    }
                }

                compileArguments(allOutputs)
            }.flatMap {
                it.map { it.joinToString(File.pathSeparator + File.pathSeparator) }
            }

            jvmArguments.add(compileArgument("-Dfabric.classPathGroups=", modClasses))

            mainClass.set(
                sourceSet.flatMap {
                    project.configurations.named(it.runtimeClasspathConfigurationName)
                }.map {
                    val view = it.incoming.artifactView {
                        it.componentFilter {
                            it is ModuleComponentIdentifier && it.group == "net.fabricmc" && it.module == "fabric-loader"
                        }
                    }

                    val fabricInstaller = loadFabricInstaller(view.files, false)!!

                    fabricInstaller.mainClass.sidedMain()
                },
            )

            jvmArguments.add(compileArgument("-Dfabric.remapClasspathFile=", data.writeRemapClasspathTask.flatMap(WriteClasspathFile::output)))
        }
    }

    fun client(action: Action<FabricRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(FabricRunConfigurationData::class.java)

        action.execute(data)

        client(data)
    }

    private fun addAssets(data: FabricRunConfigurationData) {
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

    private fun client(data: FabricRunConfigurationData) {
        defaults(data, FabricInstaller.MainClass::client)
        addAssets(data)

        defaults.configuration.apply {
            val nativesDirectory = data.extractNativesTask.flatMap(ExtractNatives::destinationDirectory)

            jvmArguments.add(compileArgument("-Djava.library.path=", nativesDirectory))
            jvmArguments.add(compileArgument("-Dorg.lwjgl.librarypath=", nativesDirectory))

            beforeRun.add(data.extractNativesTask)
        }
    }

    private fun server(data: FabricRunConfigurationData) {
        defaults(data, FabricInstaller.MainClass::server)

        defaults.configuration.arguments("nogui")
    }

    fun server(action: Action<FabricRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(FabricRunConfigurationData::class.java)

        action.execute(data)

        server(data)
    }

    fun data(action: Action<FabricDatagenRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(FabricDatagenRunConfigurationData::class.java)

        action.execute(data)

        defaults(data, FabricInstaller.MainClass::server)
        data(data)
    }

    fun clientData(action: Action<FabricDatagenRunConfigurationData>) {
        // Fabric doesn't have a dedicated client data entrypoint or properties
        data(action)
    }

    private fun data(data: FabricDatagenRunConfigurationData) {
        defaults.configuration.apply {
            jvmArguments.add("-Dfabric-api.datagen")
            jvmArguments.add(compileArgument("-Dfabric-api.datagen.output-dir=", data.outputDirectory))
            jvmArguments.add(compileArgument("-Dfabric-api.datagen.modid=", data.modId))
        }
    }

    private fun gameTest() {
        defaults.configuration.jvmArguments(
            "-Dfabric-api.gametest",
            "-Dfabric.autoTest",
        )
    }

    fun gameTestServer(action: Action<FabricRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(FabricRunConfigurationData::class.java)
        action.execute(data)

        server(data)
        gameTest()
    }

    fun gameTestClient(action: Action<FabricRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(FabricRunConfigurationData::class.java)
        action.execute(data)

        client(data)
        gameTest()
    }
}

interface FabricRunConfigurationData : RunConfigurationData {
    val writeRemapClasspathTask: Property<WriteClasspathFile>
        @Input
        get
}

interface FabricDatagenRunConfigurationData : FabricRunConfigurationData, DatagenRunConfigurationData
