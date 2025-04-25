package net.msrandom.minecraftcodev.mixins.mixin

import net.fabricmc.mappingio.tree.MappingTreeView
import org.spongepowered.asm.mixin.extensibility.IRemapper

class MappingIoRemapperAdapter(
    val mappings: MappingTreeView,
    sourceNamespace: String,
    targetNamespace: String
) : IRemapper {
    private val sourceId = mappings.getNamespaceId(sourceNamespace)
    private val targetId = mappings.getNamespaceId(targetNamespace)

    private val methods = HashMap<String, String>()
    private val fields = HashMap<String, String>()

    init {
        for (classMapping in mappings.classes) {
            for (methodMapping in classMapping.methods) {
                val sourceName = methodMapping.getName(sourceId) ?: continue
                val targetName = methodMapping.getName(targetId) ?: continue
                methods.put(sourceName, targetName)
            }
            for (fieldMapping in classMapping.fields) {
                val sourceName = fieldMapping.getName(sourceId) ?: continue
                val targetName = fieldMapping.getName(targetId) ?: continue
                fields.put(sourceName, targetName)
            }
        }
    }

    override fun mapMethodName(owner: String?, name: String?, desc: String?) = methods[name] ?: name

    override fun mapFieldName(owner: String?, name: String?, desc: String?) = fields[name] ?: name

    override fun map(typeName: String) =
        mappings.mapClassName(typeName, sourceId, targetId)

    override fun unmap(typeName: String) =
        mappings.mapClassName(typeName, targetId, sourceId)

    override fun mapDesc(desc: String) =
        mappings.mapDesc(desc, sourceId, targetId)

    override fun unmapDesc(desc: String) =
        mappings.mapDesc(desc, targetId, sourceId)
}