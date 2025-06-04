package net.msrandom.minecraftcodev.runs

import kotlinx.serialization.Serializable

@Serializable
data class ModOutputs(
    val modId: String,
    val paths: List<String>,
)
