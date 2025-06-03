package net.msrandom.minecraftcodev.forge.runs

import kotlinx.serialization.json.decodeFromStream
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.resolve.MinecraftVersionMetadata
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.UserdevConfig
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import net.msrandom.minecraftcodev.forge.task.GenerateMcpToSrg
import net.msrandom.minecraftcodev.runs.DatagenRunConfigurationData
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import net.msrandom.minecraftcodev.runs.ModOutputs
import net.msrandom.minecraftcodev.runs.RunConfigurationData
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer.Companion.getManifest
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import java.io.File

open class ForgeRunsDefaultsContainer(
    private val defaults: RunConfigurationDefaultsContainer,
) {
    private fun loadUserdev(patches: FileCollection): UserdevConfig {
        var config: UserdevConfig? = null

        for (file in patches) {
            val isUserdev = MinecraftCodevForgePlugin.userdevConfig(file) {
                config = it
            }

            if (isUserdev) break
        }

        return config ?: throw UnsupportedOperationException("Patches $patches did not contain Forge userdev.")
    }

    private fun MinecraftRunConfiguration.getUserdevData(patches: FileCollection): Provider<UserdevConfig> {
        if (patches.isEmpty) {
            val configuration = sourceSet.flatMap { project.configurations.named(it.patchesConfigurationName) }

            return configuration.map(::loadUserdev)
        }

        return project.provider {
            loadUserdev(patches)
        }
    }

    private fun MinecraftRunConfiguration.addArgs(
        manifest: MinecraftVersionMetadata,
        config: UserdevConfig,
        arguments: MutableList<Any?>,
        existing: List<String>,
        data: ForgeRunConfigurationData,
    ) {
        arguments.addAll(
            existing.map {
                if (it.startsWith('{')) {
                    resolveTemplate(
                        manifest,
                        config,
                        it.substring(1, it.length - 1),
                        data,
                    )
                } else {
                    it
                }
            },
        )
    }

    private fun MinecraftRunConfiguration.resolveTemplate(
        manifest: MinecraftVersionMetadata,
        config: UserdevConfig,
        template: String,
        data: ForgeRunConfigurationData,
    ): Any? =
        when (template) {
            "asset_index" -> manifest.assets
            "assets_root" -> data.downloadAssetsTask.flatMap(DownloadAssets::assetsDirectory)

            "modules" -> {
                val configuration = sourceSet.flatMap {
                    project.configurations.named(it.runtimeClasspathConfigurationName)
                }

                val moduleDependencies =
                    config.modules.map {
                        val dependency = project.dependencies.create(it)

                        dependency.group to dependency.name
                    }

                val moduleArtifactView =
                    configuration.map {
                        it.incoming.artifactView { viewConfiguration ->
                            viewConfiguration.componentFilter { component ->
                                component is ModuleComponentIdentifier && (component.group to component.module) in moduleDependencies
                            }
                        }
                    }

                moduleArtifactView.map { it.files.joinToString(File.pathSeparator) }
            }

            "MC_VERSION" -> manifest.id
            "mcp_mappings" -> "minecraft-codev.mappings"
            "source_roots" -> {
                val modClasses = project.provider {
                    val allOutputs = data.modOutputs.map {
                        val outputs = it.inputStream().use {
                            val outputs = json.decodeFromStream<ModOutputs>(it)

                            outputs.paths.map {
                                compileArgument(outputs.modId, "%%", project.rootProject.layout.projectDirectory.dir(it))
                            }
                        }

                        compileArguments(outputs).map {
                            it.joinToString(File.pathSeparator)
                        }
                    }

                    compileArguments(allOutputs)
                }.flatMap {
                    it.map { it.joinToString(File.pathSeparator) }
                }

                modClasses
            }

            "mcp_to_srg" -> data.generateMcpToSrg.flatMap(GenerateMcpToSrg::srg)
            "minecraft_classpath_file" -> data.writeLegacyClasspathTask.flatMap(WriteClasspathFile::output)
            "natives" -> data.extractNativesTask.flatMap(ExtractNatives::destinationDirectory)

            else -> {
                project.logger.warn("Unknown Forge userdev run configuration template $template")
                template
            }
        }

    private fun MinecraftRunConfiguration.addData(
        caller: String,
        data: ForgeRunConfigurationData,
        runType: (UserdevConfig.Runs) -> UserdevConfig.Run?,
    ) {
        val configProvider = getUserdevData(data.patches)

        val getRun: UserdevConfig.() -> UserdevConfig.Run = {
            runType(runs)
                ?: throw UnsupportedOperationException("Attempted to use $caller run configuration which doesn't exist.")
        }

        beforeRun.addAll(configProvider.flatMap {
            val list = project.objects.listProperty(Task::class.java)

            val hasAssets = it.getRun().args.contains("{assets_root}") ||
                    it.getRun().env.containsValue("{assets_root}")

            val hasNatives = it.getRun().env.containsValue("{natives}")

            val hasLegacyClasspath = it.getRun().props.containsValue("{minecraft_classpath_file}")

            if (hasAssets) {
                list.add(data.downloadAssetsTask)
            }

            if (hasNatives) {
                list.add(data.extractNativesTask)
            }

            if (hasLegacyClasspath) {
                list.add(data.writeLegacyClasspathTask)
            }

            if (data.generateMcpToSrg.isPresent) {
                list.add(data.generateMcpToSrg)
            }

            list
        })

        jvmArguments.addAll(data.generateMcpToSrg.flatMap(GenerateMcpToSrg::srg).flatMap {
            compileArguments(listOf(
                "-Dmixin.env.remapRefMap=true",
                compileArgument("-Dmixin.env.refMapRemappingFile=", it),
            ))
        }.orElse(emptyList()))

        if (SystemUtils.IS_OS_MAC_OSX) {
            defaults.configuration.jvmArguments("-XstartOnFirstThread")
        }

        val manifestProvider = getManifest(data.minecraftVersion)

        mainClass.set(configProvider.map { it.getRun().main })

        val zipped = manifestProvider.zip(configProvider, ::Pair)

        environment.putAll(
            zipped.flatMap { (manifest, userdevConfig) ->
                project.objects.mapProperty(String::class.java, String::class.java).apply {
                    for ((key, value) in userdevConfig.getRun().env) {
                        val argument =
                            if (value.startsWith('$')) {
                                resolveTemplate(
                                    manifest,
                                    userdevConfig,
                                    value.substring(2, value.length - 1),
                                    data,
                                )
                            } else if (value.startsWith('{')) {
                                resolveTemplate(
                                    manifest,
                                    userdevConfig,
                                    value.substring(1, value.length - 1),
                                    data,
                                )
                            } else {
                                value
                            }

                        put(key, compileArgument(argument))
                    }
                }
            },
        )

        arguments.addAll(
            zipped.flatMap { (manifest, userdevConfig) ->
                val arguments = mutableListOf<Any?>()

                addArgs(
                    manifest,
                    userdevConfig,
                    arguments,
                    userdevConfig.getRun().args,
                    data,
                )

                val mixinConfigs = project.provider { data.mixinConfigs }.flatMap { compileArguments(it.map { compileArgument("--mixin.config=", it.name) }) }

                compileArguments(arguments).apply {
                    addAll(mixinConfigs)
                }
            },
        )

        jvmArguments.addAll(
            zipped.flatMap { (manifest, userdevConfig) ->
                val run = userdevConfig.getRun()
                val jvmArguments = mutableListOf<Any?>()

                addArgs(
                    manifest,
                    userdevConfig,
                    jvmArguments,
                    run.jvmArgs,
                    data,
                )

                for ((key, value) in run.props) {
                    if (value.startsWith('{')) {
                        val template = value.substring(1, value.length - 1)
                        jvmArguments.add(
                            compileArgument(
                                "-D$key=",
                                resolveTemplate(
                                    manifest,
                                    userdevConfig,
                                    template,
                                    data,
                                ),
                            ),
                        )
                    } else {
                        jvmArguments.add("-D$key=$value")
                    }
                }

                compileArguments(jvmArguments)
            },
        )
    }

    fun client(action: Action<ForgeRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(ForgeRunConfigurationData::class.java)

        action.execute(data)

        defaults.configuration.apply {
            addData(::client.name, data, UserdevConfig.Runs::client)
        }
    }

    fun server(action: Action<ForgeRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(ForgeRunConfigurationData::class.java)

        action.execute(data)

        defaults.configuration.apply {
            addData(::server.name, data, UserdevConfig.Runs::server)
        }
    }

    private fun data(data: ForgeDatagenRunConfigurationData) {
        defaults.configuration.apply {
            arguments.add(compileArgument("--mod=", data.modId))
            arguments.add("--all")
            arguments.add(compileArgument("--output=", data.outputDirectory))
            arguments.add(compileArgument("--existing=", sourceSet.map { it.output.resourcesDir!! }))
            arguments.add(compileArgument("--existing=", data.mainResources))
        }
    }

    fun data(action: Action<ForgeDatagenRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(ForgeDatagenRunConfigurationData::class.java)

        action.execute(data)

        data(data)
        defaults.configuration.addData("data", data) { it.data ?: it.serverData }
    }

    fun clientData(action: Action<ForgeClientDatagenRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(ForgeClientDatagenRunConfigurationData::class.java)

        action.execute(data)

        data(data)

        defaults.configuration.apply {
            arguments.add(compileArgument("--existing=", data.commonOutputDirectory))

            addData(::clientData.name, data, UserdevConfig.Runs::clientData)
        }
    }

    fun gameTestServer(action: Action<ForgeRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance(ForgeRunConfigurationData::class.java)

        action.execute(data)

        defaults.configuration.apply {
            sourceSet.convention(project.extension<SourceSetContainer>().named(SourceSet.TEST_SOURCE_SET_NAME))

            addData(::gameTestServer.name, data, UserdevConfig.Runs::gameTestServer)
        }
    }
}

interface ForgeRunConfigurationData : RunConfigurationData {
    val patches: ConfigurableFileCollection
        @InputFiles
        get

    val mixinConfigs: ConfigurableFileCollection
        @InputFiles
        get

    val writeLegacyClasspathTask: Property<WriteClasspathFile>
        @Input
        get

    val generateMcpToSrg: Property<GenerateMcpToSrg>
        @Optional
        @Input
        get
}

interface ForgeDatagenRunConfigurationData :
    ForgeRunConfigurationData,
    DatagenRunConfigurationData {
        val mainResources: DirectoryProperty
            @Input get
    }

interface ForgeClientDatagenRunConfigurationData : ForgeDatagenRunConfigurationData {
    val commonOutputDirectory: DirectoryProperty
        @InputDirectory get
}
