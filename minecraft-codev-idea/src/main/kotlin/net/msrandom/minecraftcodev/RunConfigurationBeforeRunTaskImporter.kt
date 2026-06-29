package net.msrandom.minecraftcodev

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.BeforeRunTaskImporter
import com.intellij.openapi.project.Project
import com.intellij.util.ObjectUtils.consumeIfCast

class RunConfigurationBeforeRunTaskImporter : BeforeRunTaskImporter {
    override fun process(
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
        runConfiguration: RunConfiguration,
        beforeRunTasks: MutableList<BeforeRunTask<*>>,
        configurationData: MutableMap<String, Any>
    ): MutableList<BeforeRunTask<*>> {
        val taskProvider =
            BeforeRunTaskProvider.getProvider(project, RunConfigurationBeforeRunProvider.ID) ?: return beforeRunTasks
        val task = taskProvider.createTask(runConfiguration) ?: return beforeRunTasks
        val runManager = RunManagerImpl.getInstanceImpl(project)

        val configId = configurationData["configuration"]

        if (configId !is String) {
            return beforeRunTasks
        }

        runManager.getConfigurationById(configId)?.let { targetSettings ->
            task.setSettingsWithTarget(targetSettings, null)

            task.isEnabled = true

            val taskExists = beforeRunTasks.filterIsInstance<RunConfigurableBeforeRunTask>().any {
                val settings = it.settings

                settings != null && settings.name == targetSettings.name && settings.type.id == targetSettings.type.id
            }

            if (!taskExists) {
                beforeRunTasks.add(task)
            }
        }

        return beforeRunTasks
    }

    override fun canImport(typeName: String) = typeName == "runConfiguration"
}
