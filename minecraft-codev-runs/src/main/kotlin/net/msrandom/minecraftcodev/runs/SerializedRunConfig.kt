package net.msrandom.minecraftcodev.runs

import kotlinx.serialization.Serializable

@Serializable
data class SerializedRunConfig(
    val arguments: List<String>,
    val jvmArguments: List<String>,
    val properties: Map<String, String>,
    val environment: Map<String, String>,
)
