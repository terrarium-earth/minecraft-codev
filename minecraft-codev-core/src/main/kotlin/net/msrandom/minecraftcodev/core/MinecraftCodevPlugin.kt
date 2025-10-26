package net.msrandom.minecraftcodev.core

import kotlinx.serialization.json.Json
import net.msrandom.minecraftcodev.core.utils.applyPlugin
import org.gradle.api.Plugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.apply

open class MinecraftCodevPlugin<T : PluginAware> : Plugin<T> {
    override fun apply(target: T) =
        applyPlugin(target) {
            apply<JavaPlugin>()
        }

    companion object {
        val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }
    }
}
