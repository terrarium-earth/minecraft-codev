package net.msrandom.minecraftcodev.core.utils

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.getByType

// TODO This doesn't really need to exist but it's used everywhere, should it be removed?
// @Deprecated(replaceWith = ReplaceWith(expression = "this.extensions.getByType<T>()", imports = ["org.gradle.kotlin.dsl.getByType"]), message = "Redundant with the kotlin-dsl")
inline fun <reified T> ExtensionAware.extension(): T = extensions.getByType()
