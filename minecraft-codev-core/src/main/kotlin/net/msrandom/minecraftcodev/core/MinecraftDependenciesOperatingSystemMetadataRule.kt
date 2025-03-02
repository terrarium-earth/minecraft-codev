package net.msrandom.minecraftcodev.core

import net.msrandom.minecraftcodev.core.resolve.MinecraftVersionMetadata
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.model.ObjectFactory
import java.io.File
import javax.inject.Inject

@CacheableRule
abstract class MinecraftDependenciesOperatingSystemMetadataRule @Inject constructor(
    private val cacheDirectory: File,
    private val versions: List<String>,
    private val versionManifestUrl: String,
    private val isOffline: Boolean,
) : ComponentMetadataRule {
    abstract val objectFactory: ObjectFactory
        @Inject get

    override fun execute(context: ComponentMetadataContext) {
        val versionList = getVersionList(cacheDirectory.toPath(), versionManifestUrl, isOffline)

        val id = context.details.id

        val libraries = versions.asSequence().flatMap {
            versionList.version(it).libraries
        }.filter {
            it.name.group == id.group && it.name.module == id.name && it.name.version == id.version
        }

        // Collect list of systems to add classifiers for
        val systems = libraries.flatMapTo(hashSetOf()) { library ->
            library.rules.mapNotNull { it.os?.name } + library.natives.keys
        }

        val operatingSystemFiles = systems.associate { system ->
            system to buildSet {
                for (library in libraries) {
                    if (library.rules.isEmpty()) {
                        add(library.downloads.artifact!!.path.substringAfterLast('/'))
                        continue
                    }

                    val applicableRules = library.rules.filter { it.os?.name == system }

                    if (applicableRules.isEmpty()) {
                        continue
                    }

                    if (applicableRules.any { it.action == MinecraftVersionMetadata.RuleAction.Disallow }) {
                        continue
                    }

                    if (applicableRules.all { it.action == MinecraftVersionMetadata.RuleAction.Allow }) {
                        add(library.downloads.artifact!!.path.substringAfterLast('/'))

                        if (library.name.classifier == null) {
                            val download = library.downloads.classifiers[system] ?: continue

                            download.path.substringAfterLast('/')
                        }
                    }
                }
            }
        }

        for ((operatingSystem, files) in operatingSystemFiles) {
            context.details.maybeAddVariant(operatingSystem, "runtime") { variant ->
                variant.attributes { attribute ->
                    attribute.attribute(
                        MinecraftOperatingSystemAttribute.attribute,
                        objectFactory.named(MinecraftOperatingSystemAttribute::class.java, operatingSystem),
                    )
                }

                variant.withFiles { it ->
                    it.removeAllFiles()

                    for (file in files) {
                        it.addFile(file)
                    }
                }
            }

            context.details.maybeAddVariant(operatingSystem, "runtimeElements") { variant ->
                variant.attributes { attribute ->
                    attribute.attribute(
                        MinecraftOperatingSystemAttribute.attribute,
                        objectFactory.named(MinecraftOperatingSystemAttribute::class.java, operatingSystem),
                    )
                }

                variant.withFiles { it ->
                    it.removeAllFiles()

                    for (file in files) {
                        it.addFile(file)
                    }
                }
            }
        }
    }
}
