package net.msrandom.minecraftcodev.runs

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class ModOutputs(
    val modId: String,
    val paths: List<Path>,
)
