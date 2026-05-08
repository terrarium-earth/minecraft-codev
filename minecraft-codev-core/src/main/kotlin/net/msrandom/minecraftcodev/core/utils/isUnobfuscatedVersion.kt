package net.msrandom.minecraftcodev.core.utils

/**
 * Check if a version is unobfuscated based on its name.
 * This has been decided to be better than checking the version manifest directly to avoid downloading the version manifest at configuration time(as opposed to in a component metadata rule).
 * Note that rubydung versions are intentionally not here, as funnily enough they were not obfuscated.
 * This function should be both fast and accurate
 */
fun isUnobfuscatedVersion(version: String) =
    !version.startsWith("1.") && // Standard obfuscated release versions
            "w" !in version && // Obfuscated snapshot versions
            version != "3D Shareware v1.34" && // Self-explanatory
            !version.startsWith("b") && // Beta
            !version.startsWith("a") && !version.startsWith("inf") && !version.startsWith("c")
