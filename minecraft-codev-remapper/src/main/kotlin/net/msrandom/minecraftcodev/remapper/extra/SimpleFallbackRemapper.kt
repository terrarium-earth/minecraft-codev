package net.msrandom.minecraftcodev.remapper.extra

import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.commons.Remapper

class SimpleFallbackRemapper(
    private val mappings: MappingTreeView,
    private val sourceNamespaceId: Int,
    private val targetNamespaceId: Int,
) : Remapper() {
    override fun mapMethodName(
        owner: String,
        name: String,
        descriptor: String,
    ): String = mappings
        .getMethod(owner, name, descriptor, sourceNamespaceId)
        ?.getName(targetNamespaceId)
        ?: super.mapMethodName(owner, name, descriptor)

    override fun mapFieldName(
        owner: String,
        name: String,
        descriptor: String,
    ): String = mappings
        .getField(owner, name, descriptor, sourceNamespaceId)
        ?.getName(targetNamespaceId)
        ?: super.mapMethodName(owner, name, descriptor)
}
