package net.msrandom.minecraftcodev.remapper

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import net.msrandom.minecraftcodev.remapper.dependency.getNamespaceId

internal fun mappingProvider(mappings: MappingTreeView, sourceNamespace: String, targetNamespace: String) = IMappingProvider {
    val rebuild = mappings.srcNamespace != sourceNamespace

    val tree =
        if (rebuild) {
            val newTree = MemoryMappingTree()

            mappings.accept(MappingSourceNsSwitch(newTree, sourceNamespace))

            newTree
        } else {
            mappings
        }

    tree.accept(
        object : MappingVisitor {
            var targetNamespaceId: Int = MappingTreeView.NULL_NAMESPACE_ID

            lateinit var currentClass: String

            lateinit var currentName: String
            var currentDesc: String? = null

            var currentLvtRowIndex: Int = -1
            var currentStartOpIndex: Int = -1
            var currentLvIndex: Int = -1

            override fun visitNamespaces(
                srcNamespace: String,
                dstNamespaces: List<String>,
            ) {
                targetNamespaceId = targetNamespace.getNamespaceId(srcNamespace, dstNamespaces)
            }

            override fun visitClass(srcName: String): Boolean {
                currentClass = srcName

                return true
            }

            override fun visitField(
                srcName: String,
                srcDesc: String?,
            ): Boolean {
                currentName = srcName
                currentDesc = srcDesc

                return true
            }

            override fun visitMethod(
                srcName: String,
                srcDesc: String?,
            ): Boolean {
                currentName = srcName
                currentDesc = srcDesc

                return true
            }

            override fun visitMethodArg(
                argPosition: Int,
                lvIndex: Int,
                srcName: String?,
            ): Boolean {
                currentLvIndex = lvIndex

                return true
            }

            override fun visitMethodVar(
                lvtRowIndex: Int,
                lvIndex: Int,
                startOpIdx: Int,
                endOpIdx: Int,
                srcName: String?
            ): Boolean {
                currentLvIndex = lvIndex
                currentStartOpIndex = startOpIdx
                currentLvtRowIndex = lvtRowIndex
                return true
            }

            override fun visitDstName(
                targetKind: MappedElementKind,
                namespace: Int,
                name: String,
            ) {
                if (namespace != targetNamespaceId) return

                if (targetKind == MappedElementKind.CLASS) {
                    return it.acceptClass(currentClass, name)
                }

                val member = IMappingProvider.Member(currentClass, currentName, currentDesc)

                when (targetKind) {
                    MappedElementKind.FIELD -> it.acceptField(member, name)
                    MappedElementKind.METHOD -> it.acceptMethod(member, name)
                    MappedElementKind.METHOD_ARG -> it.acceptMethodArg(member, currentLvIndex, name)
                    MappedElementKind.METHOD_VAR ->
                        it.acceptMethodVar(
                            member,
                            currentLvIndex,
                            currentStartOpIndex,
                            currentLvtRowIndex,
                            name,
                        )

                    else -> {}
                }
            }

            override fun visitComment(
                targetKind: MappedElementKind,
                comment: String,
            ) {
            }
        },
    )

    if (rebuild) {
        tree.accept(MappingSourceNsSwitch(mappings as MappingVisitor, mappings.srcNamespace))
    }
}
