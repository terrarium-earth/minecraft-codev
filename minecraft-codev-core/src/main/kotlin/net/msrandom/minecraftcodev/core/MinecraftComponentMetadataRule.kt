package net.msrandom.minecraftcodev.core

import net.msrandom.minecraftcodev.core.resolve.MinecraftVersionList
import net.msrandom.minecraftcodev.core.resolve.getAllDependencies
import net.msrandom.minecraftcodev.core.resolve.setupCommon
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.Dependency
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.io.path.Path

const val VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

private val cache = ConcurrentHashMap<String, MinecraftVersionList>()

fun getVersionList(
    cacheDirectory: Path,
    url: String = VERSION_MANIFEST_URL,
    isOffline: Boolean,
) = cache.computeIfAbsent(url) {
    MinecraftVersionList.load(cacheDirectory, it, isOffline)
}

@CacheableRule
abstract class MinecraftComponentMetadataRule<T : Any> @Inject constructor(
    private val cacheDirectory: File,
    private val version: String,
    private val versionManifestUrl: String,
    private val isOffline: Boolean,

    private val commonCapability: String,
    private val clientCapability: String,
) : ComponentMetadataRule {
    private fun ComponentMetadataContext.addVariantDependencies(capabilityName: String, client: Boolean) {
        details.addVariant(capabilityName, Dependency.DEFAULT_CONFIGURATION) { variant ->
            variant.withCapabilities {
                for (capability in it.capabilities) {
                    it.removeCapability(capability.group, capability.name)
                }

                it.addCapability("net.msrandom", capabilityName, "0.0.0")
            }

            variant.withDependencies { dependencies ->
                val versionList = getVersionList(cacheDirectory.toPath(), versionManifestUrl, isOffline)

                val versionMetadata = versionList.version(version)

                val sidedDependencies = if (client) {
                    getAllDependencies(versionMetadata)
                } else {
                    setupCommon(cacheDirectory.toPath(), versionMetadata, isOffline)
                }

                val versionDependencies = sidedDependencies.map(ModuleLibraryIdentifier::load)

                for (dependency in versionDependencies) {
                    dependencies.add(dependency.toString())
                }
            }
        }
    }

    override fun execute(context: ComponentMetadataContext) {
        context.addVariantDependencies(commonCapability, false)
        context.addVariantDependencies(clientCapability, true)
    }
}
