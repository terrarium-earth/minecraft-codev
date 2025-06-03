package net.msrandom.minecraftcodev.includes

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.logging.Logger
import java.io.File

data class IncludedJarInfo(
    val group: String,
    val moduleName: String,
    val artifactVersion: String,
    val versionRange: String,
    val file: File,
) {
    companion object {
        private fun fromModuleSelector(
            artifact: ResolvedArtifactResult,
            selector: ModuleComponentSelector
        ): IncludedJarInfo {
            val group = selector.group
            val name = selector.module

            val id = artifact.id.componentIdentifier

            val version = if (id is ModuleComponentIdentifier) {
                id.version
            } else {
                artifact.variant.capabilities.firstOrNull()?.version ?: selector.version
            }

            val versionRange = versionRange(selector.versionConstraint) ?: defaultVersionRange(version)

            return IncludedJarInfo(
                group,
                name,
                version,
                versionRange,
                artifact.file,
            )
        }

        private fun fromModuleComponent(
            artifact: ResolvedArtifactResult,
            id: ModuleComponentIdentifier
        ): IncludedJarInfo {
            val group = id.group
            val name = id.module
            val version = id.version

            return IncludedJarInfo(
                group,
                name,
                version,
                defaultVersionRange(version),
                artifact.file,
            )
        }

        private fun maybeFromVariantCapability(artifact: ResolvedArtifactResult): IncludedJarInfo? {
            val capability = artifact.variant.capabilities.firstOrNull()

            if (capability == null) {
                return null
            }

            val group = capability.group
            val name = capability.name
            val version = capability.version ?: "0.0.0"

            return IncludedJarInfo(
                group,
                name,
                version,
                defaultVersionRange(version),
                artifact.file
            )
        }

        private fun tryFindingInfo(
            selector: ComponentSelector?,
            artifact: ResolvedArtifactResult,
        ) = if (selector is ModuleComponentSelector) {
            fromModuleSelector(artifact, selector)
        } else {
            val id = artifact.id.componentIdentifier

            if (id is ModuleComponentIdentifier) {
                fromModuleComponent(artifact, id)
            } else {
                val info = maybeFromVariantCapability(artifact)

                // If null, then we are out of fallbacks, can only ignore the file
                info
            }
        }

        private fun collectDependencyVariants(
            dependency: DependencyResult,
            dependencies: MutableMap<ResolvedVariantResult, ComponentSelector>
        ) {
            if (dependency is UnresolvedDependencyResult) {
                throw dependency.failure
            }

            dependency as ResolvedDependencyResult

            dependencies[dependency.resolvedVariant] = dependency.requested

            for (dependency in dependency.selected.getDependenciesForVariant(dependency.resolvedVariant)) {
                collectDependencyVariants(dependency, dependencies)
            }
        }

        private fun versionRange(constraint: VersionConstraint): String? {
            fun version(value: String): String? = value.takeUnless(String::isEmpty)

            return version(constraint.strictVersion)
                ?: version(constraint.requiredVersion)
                ?: version(constraint.preferredVersion)
        }

        private fun defaultVersionRange(version: String) = "[$version,)"

        fun fromResolutionResults(
            rootComponent: ResolvedComponentResult,
            artifacts: Set<ResolvedArtifactResult>,
            logger: Logger,
        ): List<IncludedJarInfo> {
            val dependencies = buildMap {
                for (result in rootComponent.dependencies) {
                    collectDependencyVariants(result, this)
                }
            }

            val jars = mutableListOf<IncludedJarInfo>()

            for (artifact in artifacts) {
                val selector = dependencies[artifact.variant]

                val info = tryFindingInfo(selector, artifact)

                if (info == null) {
                    logger.warn("Skipping included file ${artifact.file} as its coordinates could not be determined")
                    continue
                }

                jars.add(info)
            }

            return jars
        }
    }
}
