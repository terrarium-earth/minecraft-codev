package net.msrandom.minecraftcodev.forge.mappings

import java.nio.file.FileSystem
import kotlin.io.path.ExperimentalPathApi

@OptIn(ExperimentalPathApi::class)
fun injectForgeMappingService(fileSystem: FileSystem): Boolean {
//    val injectsFolder = "forge-mapping-injects"
//    val codevForgeFS =
//        zipFileSystem(Paths.get(MinecraftCodevForgePlugin::class.java.protectionDomain.codeSource.location.toURI()))
//
//    val codevInjectPath = codevForgeFS.getPath(injectsFolder)
//    if (!codevInjectPath.exists()) return false
//
//    codevInjectPath.copyToRecursively(fileSystem.rootDirectories.first(), followLinks = true, overwrite = true)

    return true
}
