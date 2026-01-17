package net.msrandom.minecraftcodev

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project
import com.intellij.util.ObjectUtils.consumeIfCast

class CodevApplicationRunConfigurationImporter : RunConfigurationImporter {
  override fun process(project: Project, runConfiguration: RunConfiguration, cfg: Map<String, *>, modelsProvider: IdeModifiableModelsProvider) {
    if (runConfiguration !is ApplicationConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    consumeIfCast(cfg["moduleName"], String::class.java) {
        val module = modelsProvider.modifiableModuleModel.findModuleByName(it)
        if (module != null) {
          runConfiguration.setModule(module)
        }
      }

    consumeIfCast(cfg["mainClass"], String::class.java) { runConfiguration.mainClassName = it }
    consumeIfCast(cfg["jvmArgs"], String::class.java) { runConfiguration.vmParameters = it  }
    consumeIfCast(cfg["programParameters"], String::class.java) { runConfiguration.programParameters = it }

    consumeIfCast(cfg["envs"], Map::class.java) {
      @Suppress("UNCHECKED_CAST")
      runConfiguration.envs = it as MutableMap<String, String>
    }

    consumeIfCast(cfg["envFilePaths"], List::class.java) {
      @Suppress("UNCHECKED_CAST")
      runConfiguration.envFilePaths = it as List<String>
    }

    runConfiguration.isPassParentEnvs = cfg["passParentEnvs"] as? Boolean ?: true

    consumeIfCast(cfg["workingDirectory"], String::class.java) { runConfiguration.workingDirectory = it }

    runConfiguration.setIncludeProvidedScope(cfg["includeProvidedDependencies"] as? Boolean ?: false)

    consumeIfCast(cfg["shortenCommandLine"], String::class.java) {
      try {
        runConfiguration.shortenCommandLine = ShortenCommandLine.valueOf(it)
      } catch (e: IllegalArgumentException) {
        LOG.warn("Illegal value of 'shortenCommandLine': $it", e)
      }
    }
  }

  override fun canImport(typeName: String): Boolean = typeName == "codev-application"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType(
      ApplicationConfigurationType::class.java)
      .configurationFactories[0]

  companion object {
    val LOG = Logger.getInstance(CodevApplicationRunConfigurationImporter::class.java)
  }
}