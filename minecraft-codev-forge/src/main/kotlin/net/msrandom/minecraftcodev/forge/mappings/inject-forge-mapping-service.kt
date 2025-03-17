package net.msrandom.minecraftcodev.forge.mappings

import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import java.nio.file.FileSystem
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists

@OptIn(ExperimentalPathApi::class)
fun injectForgeMappingService(fileSystem: FileSystem): Boolean {
    val injectsFolder = "forge-mapping-injects"
    val codevForgeFS =
        zipFileSystem(Paths.get(MinecraftCodevForgePlugin::class.java.protectionDomain.codeSource.location.toURI()))

    val codevInjectPath = codevForgeFS.getPath(injectsFolder)
    if (!codevInjectPath.exists()) return false

    codevInjectPath.copyToRecursively(fileSystem.rootDirectories.first(), followLinks = true, overwrite = true)

    return true
}
