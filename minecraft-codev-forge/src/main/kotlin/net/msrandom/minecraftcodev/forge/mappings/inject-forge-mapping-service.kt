package net.msrandom.minecraftcodev.forge.mappings

import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.notExists

@OptIn(ExperimentalPathApi::class)
fun injectForgeMappingService(fileSystem: FileSystem): Boolean {
    val serviceFile = fileSystem.getPath("META-INF", "services", "cpw.mods.modlauncher.api.INameMappingService")

    if (serviceFile.notExists()) {
        return false
    }

    val injectsFolder = "/forge-mapping-injects"
    val codevForgeFS =
        FileSystems.getFileSystem(MinecraftCodevForgePlugin::class.java.protectionDomain.codeSource.location.toURI())

    val codevInjectPath = codevForgeFS.getPath(injectsFolder)
    if (!codevInjectPath.exists()) return false

    serviceFile.deleteExisting()

    codevInjectPath.copyToRecursively(fileSystem.rootDirectories.first(), followLinks = true, overwrite = true)

    return true
}
