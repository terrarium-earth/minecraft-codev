package net.msrandom.minecraftcodev.remapper

import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.tinyremapper.TinyRemapper
import net.msrandom.minecraftcodev.core.utils.serviceLoader
import java.nio.file.FileSystem

fun interface ExtraFileRemapper {
    operator fun invoke(
        remapper: TinyRemapper,
        mappings: MappingTreeView,
        fileSystem: FileSystem,
        sourceNamespace: String,
        targetNamespace: String,
    )
}

val extraFileRemappers = serviceLoader<ExtraFileRemapper>()

fun remapFiles(
    remapper: TinyRemapper,
    mappings: MappingTreeView,
    fileSystem: FileSystem,
    sourceNamespace: String,
    targetNamespace: String,
) {
    for (extraMapper in extraFileRemappers) {
        extraMapper(remapper, mappings, fileSystem, sourceNamespace, targetNamespace)
    }
}
