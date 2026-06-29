package net.msrandom.minecraftcodev.forge.runs

import net.msrandom.minecraftcodev.core.ModuleLibraryIdentifier
import net.msrandom.minecraftcodev.core.resolve.MinecraftVersionMetadata
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.UserdevConfig
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import net.msrandom.minecraftcodev.forge.task.GenerateMcpToSrg
import net.msrandom.minecraftcodev.runs.DatagenRunConfigurationData
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import net.msrandom.minecraftcodev.runs.RunConfigurationData
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer
import net.msrandom.minecraftcodev.runs.RunConfigurationDefaultsContainer.Companion.getManifest
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.newInstance
import java.io.File

open class ForgeRunsDefaultsContainer(
    private val defaults: RunConfigurationDefaultsContainer,
) {
    private fun loadUserdev(patches: Set<FileSystemLocation>): UserdevConfig {
        var config: UserdevConfig? = null

        for (location in patches) {
            val isUserdev = MinecraftCodevForgePlugin.userdevConfig(location.asFile) {
                config = it
            }

            if (isUserdev) break
        }

        return config ?: throw UnsupportedOperationException("Patches $patches did not contain Forge userdev.")
    }

    private fun getUserdevData(patches: FileCollection, patchesConfiguration: Provider<Configuration>, providers: ProviderFactory) =
        patches.elements.zip(patchesConfiguration) { elements, configuration ->
            if (elements.isEmpty()) {
                configuration.elements.map(::loadUserdev)
            } else {
                providers.provider { loadUserdev(elements) }
            }
        }.flatMap { it }

    private fun addArgs(
        manifest: MinecraftVersionMetadata,
        config: UserdevConfig,
        arguments: MutableList<Any?>,
        existing: List<String>,
        data: ForgeRunConfigurationData,
        runtimeClasspathConfiguration: Provider<Configuration>,
        objectFactory: ObjectFactory,
        logger: Logger,
    ) {
        arguments.addAll(
            existing.map {
                if (it.startsWith('{')) {
                    resolveTemplate(
                        manifest,
                        config,
                        it.substring(1, it.length - 1),
                        data,
                        runtimeClasspathConfiguration,
                        objectFactory,
                        logger,
                    )
                } else {
                    it
                }
            },
        )
    }

    private fun resolveTemplate(
        manifest: MinecraftVersionMetadata,
        config: UserdevConfig,
        template: String,
        data: ForgeRunConfigurationData,
        runtimeClasspathConfiguration: Provider<Configuration>,
        objectFactory: ObjectFactory,
        logger: Logger,
    ): Any? =
        when (template) {
            "asset_index" -> manifest.assets
            "assets_root" -> data.downloadAssetsTask.flatMap(DownloadAssets::assetsDirectory)

            "modules" -> {
                val moduleDependencies =
                    config.modules.map {
                        val dependency = ModuleLibraryIdentifier.load(it)

                        dependency.group to dependency.module
                    }

                val moduleArtifactView = runtimeClasspathConfiguration.flatMap {
                    it.incoming.artifactView {
                        componentFilter { component ->
                            component is ModuleComponentIdentifier && (component.group to component.module) in moduleDependencies
                        }
                    }.files.elements
                }

                moduleArtifactView.map {
                    it.joinToString(File.pathSeparator, transform = { it.asFile.absolutePath })
                }
            }

            "MC_VERSION" -> manifest.id
            "mcp_mappings" -> "minecraft-codev.mappings"
            "source_roots" -> {
                val byModId = data.modOutputs.get().flatten()

                val outputs = byModId.flatMap { (modId, files) ->
                    files.map {
                        MinecraftRunConfiguration.compileArgument(objectFactory, modId, "%%", it)
                    }
                }

                val allOutputs = MinecraftRunConfiguration.compileArguments(objectFactory, outputs).map {
                    it.joinToString(File.pathSeparator)
                }

                allOutputs
            }

            "mcp_to_srg" -> data.generateMcpToSrg.flatMap(GenerateMcpToSrg::srg)
            "minecraft_classpath_file" -> data.writeLegacyClasspathTask.flatMap(WriteClasspathFile::output)
            "natives" -> data.extractNativesTask.flatMap(ExtractNatives::destinationDirectory)

            else -> {
                logger.warn("Unknown Forge userdev run configuration template $template")
                template
            }
        }

    private fun MinecraftRunConfiguration.addData(
        caller: String,
        data: ForgeRunConfigurationData,
        runType: (UserdevConfig.Runs) -> UserdevConfig.Run?,
    ) {
        val patchesConfiguration = sourceSet.flatMap { project.configurations.named(it.patchesConfigurationName) }
        val configProvider = getUserdevData(data.patches, patchesConfiguration, project.providers)

        val getRun: UserdevConfig.() -> UserdevConfig.Run = {
            runType(runs)
                ?: throw UnsupportedOperationException("Attempted to use $caller run configuration which doesn't exist.")
        }

        val objects = project.objects
        val configurations = project.configurations
        val logger = project.logger
        val runtimeClasspathConfiguration = sourceSet.flatMap { configurations.named(it.runtimeClasspathConfigurationName) }

        properties.putAll(data.generateMcpToSrg.flatMap(GenerateMcpToSrg::srg).flatMap {
            objects.mapProperty<String, String>().apply {
                put("mixin.env.remapRefMap", "true")
                put("mixin.env.refMapRemappingFile", MinecraftRunConfiguration.compileArgument(objects, it))
            }
        }.orElse(emptyMap()))

        if (SystemUtils.IS_OS_MAC_OSX) {
            defaults.configuration.jvmArguments("-XstartOnFirstThread")
        }

        val manifestProvider = getManifest(data.minecraftVersion)

        jvmVersion.set(manifestProvider.map { it.javaVersion.majorVersion })
        mainClass.set(configProvider.map { it.getRun().main })

        val zipped = manifestProvider.zip(configProvider, ::Pair)

        environment.putAll(
            zipped.flatMap { (manifest, userdevConfig) ->
                objects.mapProperty<String, String>().apply {
                    for ((key, value) in userdevConfig.getRun().env) {
                        val argument =
                            if (value.startsWith('$')) {
                                resolveTemplate(
                                    manifest,
                                    userdevConfig,
                                    value.substring(2, value.length - 1),
                                    data,
                                    runtimeClasspathConfiguration,
                                    objects,
                                    logger,
                                )
                            } else if (value.startsWith('{')) {
                                resolveTemplate(
                                    manifest,
                                    userdevConfig,
                                    value.substring(1, value.length - 1),
                                    data,
                                    runtimeClasspathConfiguration,
                                    objects,
                                    logger,
                                )
                            } else {
                                value
                            }

                        put(key, MinecraftRunConfiguration.compileArgument(objects, argument))
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
                    runtimeClasspathConfiguration,
                    objects,
                    logger,
                )

                val mixinConfigs = data.mixinConfigs.elements.flatMap {
                    MinecraftRunConfiguration.compileArguments(
                        objects,
                        it.map { MinecraftRunConfiguration.compileArgument(objects, "--mixin.config=", it.asFile.name) },
                    )
                }

                MinecraftRunConfiguration.compileArguments(objects, arguments).apply {
                    addAll(mixinConfigs)
                }
            },
        )

        this@addData.jvmArguments.addAll(
            zipped.flatMap { (manifest, userdevConfig) ->
                val run = userdevConfig.getRun()
                val jvmArguments = mutableListOf<Any?>()

                addArgs(
                    manifest,
                    userdevConfig,
                    jvmArguments,
                    run.jvmArgs,
                    data,
                    runtimeClasspathConfiguration,
                    objects,
                    logger,
                )

                MinecraftRunConfiguration.compileArguments(objects, jvmArguments)
            }
        )

        properties.putAll(
            zipped.flatMap { (manifest, userdevConfig) ->
                val run = userdevConfig.getRun()
                val properties = objects.mapProperty<String, String>()

                for ((key, value) in run.props) {
                    if (value.startsWith('{')) {
                        val template = value.substring(1, value.length - 1)
                        properties.put(
                            key,
                            MinecraftRunConfiguration.compileArgument(objects, resolveTemplate(
                                manifest,
                                userdevConfig,
                                template,
                                data,
                                runtimeClasspathConfiguration,
                                objects,
                                logger,
                            ))
                        )
                    } else {
                        properties.put(key, value)
                    }
                }

                properties
            },
        )
    }

    fun client(action: Action<ForgeRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance<ForgeRunConfigurationData>()

        action.execute(data)

        defaults.configuration.apply {
            addData(::client.name, data, UserdevConfig.Runs::client)
        }
    }

    fun server(action: Action<ForgeRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance<ForgeRunConfigurationData>()

        action.execute(data)

        defaults.configuration.apply {
            addData(::server.name, data, UserdevConfig.Runs::server)
        }
    }

    private fun data(data: ForgeDatagenRunConfigurationData) {
        defaults.configuration.apply {
            val objects = project.objects

            arguments.add(MinecraftRunConfiguration.compileArgument(objects, "--mod=", data.modId))
            arguments.add("--all")
            arguments.add(MinecraftRunConfiguration.compileArgument(objects, "--output=", data.outputDirectory))
            arguments.add(MinecraftRunConfiguration.compileArgument(objects, "--existing=", sourceSet.map { it.output.resourcesDir!! }))
            arguments.add(MinecraftRunConfiguration.compileArgument(objects, "--existing=", data.mainResources))
        }
    }

    fun data(action: Action<ForgeDatagenRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance<ForgeDatagenRunConfigurationData>()

        action.execute(data)

        data(data)
        defaults.configuration.addData("data", data) { it.data ?: it.serverData }
    }

    fun clientData(action: Action<ForgeClientDatagenRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance<ForgeClientDatagenRunConfigurationData>()

        action.execute(data)

        data(data)

        defaults.configuration.apply {
            arguments.add(MinecraftRunConfiguration.compileArgument(project.objects, "--existing=", data.commonOutputDirectory))

            addData(::clientData.name, data, UserdevConfig.Runs::clientData)
        }
    }

    fun gameTestServer(action: Action<ForgeRunConfigurationData>) {
        val data = defaults.configuration.project.objects.newInstance<ForgeRunConfigurationData>()

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
