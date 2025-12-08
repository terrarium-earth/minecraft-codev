package net.msrandom.minecraftcodev.includes

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.kotlin.dsl.newInstance

interface IncludedJarInfo {
    val group: Property<String>
        @Input get

    val moduleName: Property<String>
        @Input get

    val artifactVersion: Property<String>
        @Input get

    val versionRange: Property<String>
        @Input get

    val file: RegularFileProperty
        @InputFile get

    companion object {
        private fun tryFindInfo(
            objectFactory: ObjectFactory,
            selector: ComponentSelector?,
            artifact: ResolvedArtifactResult,
        ): IncludedJarInfo? {
            val componentId = artifact.id.componentIdentifier
            val moduleSelector = selector as? ModuleComponentSelector

            val mainCapability = when (componentId) {
                is ModuleComponentIdentifier ->
                    // Try to find first variant that matches identifier, for consistency(matches MDG behavior)
                    artifact.variant.capabilities.firstOrNull {
                        it.group == componentId.group &&
                                it.name == componentId.module &&
                                it.version == componentId.version
                    }

                is ProjectComponentIdentifier ->
                    // Not in parity with MDG, but gets the capability that matches the project name for consistency with module capability behavior
                    artifact.variant.capabilities.firstOrNull {
                        it.name == componentId.projectName
                    }

                else -> null
            }

            val capability = mainCapability ?: artifact.variant.capabilities.firstOrNull()

            if (capability == null) {
                return null
            }

            val group = capability.group
            val name = capability.name
            val version = capability.version ?: (componentId as? ModuleComponentIdentifier)?.version ?: moduleSelector?.version ?: "0.0.0"
            val versionRange = versionRange(moduleSelector?.versionConstraint) ?: defaultVersionRange(version)

            return objectFactory.newInstance<IncludedJarInfo>().also {
                it.group.set(group)
                it.moduleName.set(name)
                it.artifactVersion.set(version)
                it.versionRange.set(versionRange)
                it.file.set(artifact.file)
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

        private fun versionRange(constraint: VersionConstraint?): String? {
            if (constraint == null) {
                return null
            }

            fun version(value: String): String? = value.takeUnless(String::isEmpty)

            return version(constraint.strictVersion)
                ?: version(constraint.requiredVersion)
                ?: version(constraint.preferredVersion)
        }

        private fun defaultVersionRange(version: String) = "[$version,)"

        fun fromResolutionResults(
            objectFactory: ObjectFactory,
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

                val info = tryFindInfo(objectFactory, selector, artifact)

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
