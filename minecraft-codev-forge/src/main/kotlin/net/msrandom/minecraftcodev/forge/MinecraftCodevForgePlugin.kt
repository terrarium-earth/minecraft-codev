package net.msrandom.minecraftcodev.forge

import kotlinx.serialization.json.decodeFromStream
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.utils.applyPlugin
import net.msrandom.minecraftcodev.core.utils.createSourceSetConfigurations
import net.msrandom.minecraftcodev.core.utils.createSourceSetElements
import net.msrandom.minecraftcodev.core.utils.disambiguateName
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.forge.runs.setupForgeRunsIntegration
import org.gradle.api.Plugin
import org.gradle.api.plugins.PluginAware
import org.gradle.api.tasks.SourceSet
import java.io.File
import java.nio.file.FileSystem
import kotlin.io.path.exists
import kotlin.io.path.inputStream

val SourceSet.patchesConfigurationName get() = disambiguateName(MinecraftCodevForgePlugin.PATCHES_CONFIGURATION)

open class MinecraftCodevForgePlugin<T : PluginAware> : Plugin<T> {
    override fun apply(target: T) =
        applyPlugin(target) {
            createSourceSetConfigurations(PATCHES_CONFIGURATION)

            createSourceSetElements {
                dependencies.add(it.runtimeOnlyConfigurationName, "net.msrandom:codev-forge-runtime:0.1.0")
            }

            setupForgeRunsIntegration()
        }

    companion object {
        const val SRG_MAPPINGS_NAMESPACE = "srg"
        const val PATCHES_CONFIGURATION = "patches"

        internal const val FORGE_MODS_TOML = "mods.toml"
        internal const val NEOFORGE_MODS_TOML = "neoforge.mods.toml"

        internal fun userdevConfig(
            file: File,
            action: FileSystem.(config: UserdevConfig) -> Unit,
        ) = zipFileSystem(file.toPath()).use { fs ->
            val configPath = fs.getPath("config.json")
            if (configPath.exists()) {
                fs.action(configPath.inputStream().use(json::decodeFromStream))
                true
            } else {
                false
            }
        }
    }
}
