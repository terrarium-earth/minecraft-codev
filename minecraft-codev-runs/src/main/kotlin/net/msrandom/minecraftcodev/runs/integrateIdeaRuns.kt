package net.msrandom.minecraftcodev.runs

import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.*
import javax.inject.Inject
import kotlin.io.path.createDirectories

private fun addDependsOn(project: Project, application: Application, config: MinecraftRunConfiguration) {
    for (other in config.dependsOn.get()) {
        application.beforeRun.add(
            project.objects.newInstance(RunConfigurationBeforeRunTask::class.java, other.name).apply {
                configuration.set(
                    project.provider {
                        "Application.${other.friendlyName}"
                    },
                )
            },
        )

        addDependsOn(project, application, other)
    }
}

private fun setupIdeaRun(project: Project, runConfigurations: RunConfigurationContainer, config: MinecraftRunConfiguration) {
    runConfigurations.register(config.friendlyName, Application::class.java) { application ->
        val workingDirectory = config.workingDirectory.asFile.get()

        workingDirectory.toPath().createDirectories()

        project.extension<IdeaModel>().module.excludeDirs.add(workingDirectory)

        application.mainClass = config.mainClass.get()
        application.workingDirectory = workingDirectory.absolutePath
        application.envs = config.environment.get()
        application.programParameters = config.arguments.get().joinToString(" ")
        application.jvmArgs = config.jvmArguments.get().joinToString(" ")

        if (config.sourceSet.isPresent) {
            application.moduleRef(project, config.sourceSet.get())
        } else {
            application.moduleRef(project)
        }

        addDependsOn(project, application, config)

        application.beforeRun.register("prepareTask", GradleTask::class.java) {
            it.task = config.prepareTask.get()
        }
    }
}

fun Project.integrateIdeaRuns() {
    if (project != rootProject) {
        return
    }

    plugins.apply(IdeaExtPlugin::class.java)

    val runConfigurations = extension<IdeaModel>().project.settings.runConfigurations

    allprojects { otherProject ->
        otherProject.plugins.withType(MinecraftCodevRunsPlugin::class.java) {
            otherProject.extension<RunsContainer>()
                .all { configuration ->
                    setupIdeaRun(otherProject, runConfigurations, configuration)
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
