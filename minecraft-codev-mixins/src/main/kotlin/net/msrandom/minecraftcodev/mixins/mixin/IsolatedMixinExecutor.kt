package net.msrandom.minecraftcodev.mixins.mixin

import com.google.common.base.Joiner
import com.google.common.collect.HashMultimap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.msrandom.minecraftcodev.core.utils.SetMultimapSerializer
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.mixins.MixinListingRule
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI
import org.spongepowered.asm.mixin.FabricUtil
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.extensibility.IMixinConfig
import org.spongepowered.asm.mixin.transformer.Config
import org.spongepowered.asm.service.MixinService
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.*

/**
 * Executor class that runs Mixin application in an isolated classloader context.
 * All Mixin framework references resolve to classes in the isolated classloader.
 * This class must be loaded by IsolatingMixinClassLoader.
 */
class IsolatedMixinExecutor {

    companion object {
        private val JOINER = Joiner.on('.')

        /**
         * Loader version to Mixin compatibility version mapping.
         * Must be in DESCENDING order (latest first).
         */
        private val VERSION_MAPPINGS = listOf(
            LoaderMixinVersionEntry("0.18.4", FabricUtil.COMPATIBILITY_0_17_0),
            LoaderMixinVersionEntry("0.17.3", FabricUtil.COMPATIBILITY_0_16_5),
            LoaderMixinVersionEntry("0.16.0", FabricUtil.COMPATIBILITY_0_14_0),
            LoaderMixinVersionEntry("0.12.0", FabricUtil.COMPATIBILITY_0_10_0),
        )

        /**
         * Compute Mixin compatibility level from minimum Fabric Loader version.
         */
        fun computeMixinCompat(minLoaderVersion: String?): Int {
            if (minLoaderVersion == null) {
                return FabricUtil.COMPATIBILITY_0_9_2
            }

            val cleanVersion = minLoaderVersion.substringBefore('+').substringBefore('-')
            val versionParts = cleanVersion.split('.')

            if (versionParts.size < 2) {
                return FabricUtil.COMPATIBILITY_0_9_2
            }

            val major = versionParts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = versionParts.getOrNull(2)?.toIntOrNull() ?: 0

            for (entry in VERSION_MAPPINGS) {
                if (isVersionGreaterOrEqual(major, minor, patch, entry.loaderVersion)) {
                    return entry.mixinVersion
                }
            }

            return FabricUtil.COMPATIBILITY_0_9_2
        }

        private fun isVersionGreaterOrEqual(
            major: Int, minor: Int, patch: Int,
            reference: String
        ): Boolean {
            val refParts = reference.split('.')
            val refMajor = refParts.getOrNull(0)?.toIntOrNull() ?: 0
            val refMinor = refParts.getOrNull(1)?.toIntOrNull() ?: 0
            val refPatch = refParts.getOrNull(2)?.toIntOrNull() ?: 0

            return when {
                major > refMajor -> true
                major < refMajor -> false
                minor > refMinor -> true
                minor < refMinor -> false
                else -> patch >= refPatch
            }
        }
    }

    private data class LoaderMixinVersionEntry(
        val loaderVersion: String,
        val mixinVersion: Int
    )

    /**
     * Extract the minimum Fabric Loader version from fabric.mod.json.
     */
    private fun extractMinLoaderVersion(fileSystem: FileSystem): String? {
        val modJson = fileSystem.getPath("/fabric.mod.json")
        if (!modJson.exists()) {
            return null
        }

        return try {
            modJson.inputStream().use {
                val json = Json.decodeFromStream<JsonObject>(it)
                parseMinLoaderVersion(json)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse minimum Fabric Loader version from fabric.mod.json content.
     */
    private fun parseMinLoaderVersion(json: JsonObject): String? {
        val depends = json["depends"]?.jsonObject ?: return null

        // Try both "fabricloader" and "fabric-loader" keys
        val loaderDep = depends["fabricloader"] ?: depends["fabric-loader"] ?: return null

        val versionSpec = when {
            loaderDep is JsonObject -> {
                loaderDep["version"]?.jsonPrimitive?.content
            }
            loaderDep.jsonPrimitive.isString -> {
                loaderDep.jsonPrimitive.content
            }
            else -> null
        } ?: return null

        return extractMinVersion(versionSpec)
    }

    /**
     * Extract minimum version from a version range string.
     */
    private fun extractMinVersion(versionSpec: String): String? {
        val trimmed = versionSpec.trim()

        // Handle ">=x.y.z" or ">x.y.z"
        if (trimmed.startsWith(">=")) {
            return trimmed.substring(2).trim().split(" ").firstOrNull()
        }
        if (trimmed.startsWith(">")) {
            return trimmed.substring(1).trim().split(" ").firstOrNull()
        }

        // Handle range format "[x.y.z, ...)"
        if (trimmed.startsWith("[")) {
            val end = trimmed.indexOfAny(charArrayOf(',', ']'))
            if (end > 1) {
                return trimmed.substring(1, end).trim()
            }
        }

        // Handle plain version string
        if (trimmed.matches(Regex("^[\\d.]+.*"))) {
            return trimmed.split(" ").firstOrNull()
        }

        return null
    }

    /**
     * Execute mixin application in an isolated context.
     * All parameters are primitive/String types for cross-classloader safety.
     */
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun execute(
        inputFilePath: String,
        outputFilePath: String,
        mixinFilePaths: List<String>,
        classpathPaths: List<String>,
        side: String,
        mappingsFilePath: String,
        sourceNamespace: String,
        targetNamespace: String,
        appliedMixinsFilePath: String
    ) {
        // 1. Initialize Mixin system (fresh in this classloader!)
        val mixinServiceClass = MixinService::class.java
        val getInstanceMethod = mixinServiceClass.getDeclaredMethod("getInstance")
        getInstanceMethod.setAccessible(true)
        val mixinService = getInstanceMethod.invoke(null) as MixinService
        val propertyServiceField = mixinServiceClass.getDeclaredField("propertyService")
        propertyServiceField.setAccessible(true)
        propertyServiceField.set(mixinService, GradleGlobalPropertyService())

        System.setProperty("mixin.service", GradleMixinService::class.java.name)

        MixinBootstrap.init()

        val service = MixinService.getService() as GradleMixinService
        service.phaseConsumer.accept(MixinEnvironment.Phase.DEFAULT)

        // 2. Convert string paths to File/Path objects
        val inputFile = File(inputFilePath).toPath()
        val outputFile = File(outputFilePath).toPath()
        val mixinFiles = mixinFilePaths.map { File(it) }
        val classpathFiles = classpathPaths.map { File(it) }
        val mappingsFile = File(mappingsFilePath)
        val appliedMixinsFile = File(appliedMixinsFilePath)

        val mixinSide = MixinEnvironment.Side.valueOf(side)

        // 3. Set up the classpath (all files including mixins and input)
        val allClasspathFiles = classpathFiles + mixinFiles + listOf(inputFile.toFile())

        service.use(allClasspathFiles, mixinSide) {
            // 4. Set up mappings
            val mappings = MemoryMappingTree()
            Tiny2FileReader.read(mappingsFile.reader(), mappings)

            MixinEnvironment.getDefaultEnvironment().remappers.add(
                MappingIoRemapperAdapter(mappings, sourceNamespace, targetNamespace)
            )

            MixinEnvironment.getDefaultEnvironment().setOption(
                MixinEnvironment.Option.REFMAP_REMAP,
                System.getProperty("mixin.env.remapRefMap", "true").toBoolean()
            )

            // 5. Set up recorder
            val appliedMixins = HashMultimap.create<String, String>()
            recorderExtension.appliedMixins = appliedMixins

            // 6. Load mixin listing rules from this classloader
            val mixinListingRules = ServiceLoader.load(
                MixinListingRule::class.java,
                this.javaClass.classLoader
            ).toList()

            // 7. Add mixin configurations and set compatibility levels
            val configToModMap = mutableMapOf<String, String?>() // config name -> min loader version

            for (mixinFile in mixinFiles + listOf(inputFile.toFile())) {
                zipFileSystem(mixinFile.toPath()).use fs@{ fs ->
                    val root = fs.getPath("/")

                    val handler = mixinListingRules.firstNotNullOfOrNull { rule ->
                        rule.load(root)
                    }

                    if (handler == null) {
                        return@fs
                    }

                    val configs = handler.list(root)
                    if (configs.isEmpty()) {
                        return@fs
                    }

                    // Get minimum loader version from fabric.mod.json
                    val minLoaderVersion = extractMinLoaderVersion(fs)

                    // Store mapping for later decoration
                    configs.forEach { config ->
                        configToModMap[config] = minLoaderVersion
                    }

                    Mixins.addConfigurations(
                        configs.toTypedArray(),
                        ContainerHandleURI(mixinFile.toPath().toUri())
                    )
                }
            }

            // 7.1 Apply compatibility decorations to configs
            for (rawConfig in Mixins.getConfigs()) {
                val configName = rawConfig.name
                val minLoaderVersion = configToModMap[configName]
                val compatLevel = computeMixinCompat(minLoaderVersion)

                val config = rawConfig.config
                if (config is IMixinConfig) {
                    config.decorate(FabricUtil.KEY_COMPATIBILITY, compatLevel)
                }
            }

            // 8. Transform classes
            outputFile.deleteIfExists()

            zipFileSystem(inputFile).use { inputFs ->
                val root = inputFs.getPath("/")

                zipFileSystem(outputFile, true).use { outputFs ->
                    root.visitFileTree(fileVisitor {
                        onVisitFile { path, _ ->
                            val outputPath = outputFs.getPath(
                                path.getName(0).name,
                                *path.drop(1).map { it.name }.toList().toTypedArray()
                            )

                            outputPath.createParentDirectories()

                            if (path.extension == "class") {
                                val pathName = JOINER.join(root.relativize(path))
                                val name = pathName.substring(0, pathName.length - ".class".length)
                                outputPath.writeBytes(transformer.transformClassBytes(name, name, path.readBytes()))
                            } else {
                                path.copyTo(
                                    outputPath,
                                    StandardCopyOption.COPY_ATTRIBUTES,
                                    StandardCopyOption.REPLACE_EXISTING
                                )
                            }
                            FileVisitResult.CONTINUE
                        }
                    })
                }
            }

            // 9. Write applied mixins
            Json.encodeToStream(
                SetMultimapSerializer(String.serializer(), String.serializer()),
                appliedMixins,
                appliedMixinsFile.outputStream()
            )
        }
    }
}
