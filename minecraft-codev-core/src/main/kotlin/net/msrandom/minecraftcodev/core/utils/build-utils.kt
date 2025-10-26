package net.msrandom.minecraftcodev.core.utils

import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.apply

fun osVersion(): String {
    val version = SystemUtils.OS_VERSION
    val versionEnd = version.indexOf('-')
    return if (versionEnd < 0) version else version.substring(0, versionEnd)
}

fun <T : PluginAware> Plugin<T>.applyPlugin(
    target: T,
    action: Project.() -> Unit = {},
) {
    val pluginClass = javaClass

    target.apply<MinecraftCodevPlugin<*>>()

    return when (target) {
        is Gradle -> {
            target.allprojects {
                plugins.apply(pluginClass)
            }
        }

        is Settings -> target.gradle.apply {
            plugin(pluginClass)
        }

        is Project -> {
            target.action()
        }

        else -> Unit
    }
}
