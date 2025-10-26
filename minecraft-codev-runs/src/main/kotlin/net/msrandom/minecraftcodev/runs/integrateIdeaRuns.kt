package net.msrandom.minecraftcodev.runs

import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.*
import javax.inject.Inject
import kotlin.io.path.createDirectories

private fun Application.addDependsOn(project: Project, config: MinecraftRunConfiguration) {
    for (other in config.dependsOn.get()) {
        beforeRun.add(
            project.objects.newInstance<RunConfigurationBeforeRunTask>(other.name).apply {
                configuration.set("Application.${other.friendlyName}")
            },
        )

        addDependsOn(project, other)
    }
}

private fun Project.setupIdeaRun(runConfigurations: RunConfigurationContainer, config: MinecraftRunConfiguration) {
    runConfigurations.register<Application>(config.friendlyName) {
        val configWorkingDir = config.workingDirectory.asFile.get()

        configWorkingDir.toPath().createDirectories()

        extension<IdeaModel>().module.excludeDirs.add(configWorkingDir)

        mainClass = config.mainClass.get()
        workingDirectory = configWorkingDir.absolutePath
        envs = config.environment.get()
        programParameters = config.arguments.get().joinToString(" ")
        jvmArgs = config.jvmArguments.get().joinToString(" ")

        if (config.sourceSet.isPresent) {
            moduleRef(project, config.sourceSet.get())
        } else {
            moduleRef(project)
        }

        addDependsOn(project, config)

        beforeRun.register<GradleTask>("prepareTask") {
            task = config.prepareTask.get()
        }
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

// Relies on IntelliJ plugin to import
abstract class RunConfigurationBeforeRunTask
@Inject
constructor(name: String) : BeforeRunTask() {
    abstract val configuration: Property<String>

    init {
        super.name = name
        type = "runConfiguration"
    }

    override fun toMap() = mapOf(*super.toMap().toList().toTypedArray(), "configuration" to configuration.get())
}
