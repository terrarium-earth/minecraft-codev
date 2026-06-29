package net.msrandom.minecraftcodev.runs

import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.*
import kotlin.io.path.createDirectories

private fun Project.setupIdeaRun(runConfigurations: RunConfigurationContainer, config: MinecraftRunConfiguration) {
    runConfigurations.register<Gradle>(config.friendlyName) {
        val configWorkingDir = config.workingDirectory.asFile.get()

        configWorkingDir.toPath().createDirectories()

        extension<IdeaModel>().module.excludeDirs.add(configWorkingDir)

        projectPath = rootProject.layout.projectDirectory.asFile.absolutePath
        taskNames = listOf(config.runTask.get().path)
    }
}

fun Project.integrateIdeaRuns() {
    if (project != rootProject) {
        return
    }

    apply<IdeaExtPlugin>()

    val runConfigurations = extension<IdeaModel>().project.settings.runConfigurations

    allprojects {
        plugins.withType(MinecraftCodevRunsPlugin::class) {
            extension<RunsContainer>()
                .all {
                    setupIdeaRun(runConfigurations, this)
                }
        }
    }
}

