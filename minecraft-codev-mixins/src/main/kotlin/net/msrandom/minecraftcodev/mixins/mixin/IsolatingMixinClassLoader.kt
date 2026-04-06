package net.msrandom.minecraftcodev.mixins.mixin

import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration

/**
 * A child-first classloader that isolates Mixin framework classes.
 * Each instance gets its own copy of static fields for isolated packages,
 * enabling multiple independent Mixin operations.
 */
class IsolatingMixinClassLoader(
    urls: Array<URL>,
    parent: ClassLoader
) : URLClassLoader(urls, parent) {

    companion object {
        private val ISOLATED_PREFIXES = listOf(
            "org.spongepowered.asm.",
            "com.llamalad7.mixinextras.",
            "org.objectweb.asm.",
            "net.fabricmc.mappingio.",
            "net.msrandom.minecraftcodev.mixins.mixin.",
        )
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // Check if already loaded
            var c = findLoadedClass(name)
            if (c != null) {
                if (resolve) resolveClass(c)
                return c
            }

            // For isolated packages, try child first (load from our URLs)
            if (ISOLATED_PREFIXES.any { name.startsWith(it) }) {
                try {
                    c = findClass(name)
                    if (resolve) resolveClass(c)
                    return c
                } catch (_: ClassNotFoundException) {
                    // Fall through to parent
                }
            }

            // Default parent-first delegation
            return super.loadClass(name, resolve)
        }
    }

    override fun getResource(name: String): URL? {
        // For isolated packages, check child first
        if (ISOLATED_PREFIXES.any { name.replace('/', '.').startsWith(it) }) {
            findResource(name)?.let { return it }
        }
        return super.getResource(name)
    }

    override fun getResources(name: String): Enumeration<URL> {
        // For service loader files and isolated resources, include child resources first
        if (name.startsWith("META-INF/services/org.spongepowered.asm.") ||
            name.startsWith("META-INF/services/net.msrandom.minecraftcodev.mixins.mixin.")
        ) {
            val childResources = findResources(name).toList()
            if (childResources.isNotEmpty()) {
                return Collections.enumeration(childResources)
            }
        }
        return super.getResources(name)
    }
}
