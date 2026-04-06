package net.msrandom.minecraftcodev.mixins

import net.msrandom.minecraftcodev.core.utils.applyPlugin
import net.msrandom.minecraftcodev.core.utils.createSourceSetConfigurations
import net.msrandom.minecraftcodev.core.utils.disambiguateName
import org.gradle.api.Plugin
import org.gradle.api.plugins.PluginAware
import org.gradle.api.tasks.SourceSet

val SourceSet.mixinsConfigurationName get() = disambiguateName(MinecraftCodevMixinsPlugin.MIXINS_CONFIGURATION)

class MinecraftCodevMixinsPlugin<T : PluginAware> : Plugin<T> {
    override fun apply(target: T) =
        applyPlugin(target) {
            createSourceSetConfigurations(MIXINS_CONFIGURATION)
            // Mixin initialization is now done in IsolatedMixinExecutor
            // within an isolated classloader for each mixin operation
        }

    companion object {
        const val MIXINS_CONFIGURATION = "mixins"
    }
}
