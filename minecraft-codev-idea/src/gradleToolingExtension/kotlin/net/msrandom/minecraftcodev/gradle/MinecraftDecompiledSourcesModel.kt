package net.msrandom.minecraftcodev.gradle

import java.io.Serializable
import java.nio.file.Path

data class DecompileTaskInfo(
    val output: Path,
    val path: String,
) : Serializable

sealed interface MinecraftDecompiledSourcesModel {
    val decompileTasks: Map<Path, DecompileTaskInfo>
}

class MinecraftDecompiledSourcesModelImpl : MinecraftDecompiledSourcesModel, Serializable {
    override val decompileTasks = mutableMapOf<Path, DecompileTaskInfo>()
}
