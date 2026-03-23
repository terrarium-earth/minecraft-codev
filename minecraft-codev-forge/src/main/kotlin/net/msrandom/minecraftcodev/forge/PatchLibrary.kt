package net.msrandom.minecraftcodev.forge

import kotlinx.serialization.Serializable

@Serializable
data class PatchLibrary(
    val version: String? = null,
    val classpath: List<String> = emptyList(),
    val args: List<String>,
)
