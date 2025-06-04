package net.msrandom.minecraftcodev.forge.task

import net.fabricmc.mappingio.format.tiny.Tiny2FileReader
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.minecraftforge.srgutils.IMappingBuilder
import net.minecraftforge.srgutils.IMappingFile
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class GenerateMcpToSrg : DefaultTask() {
    abstract val mappings: RegularFileProperty
        @InputFile get

    abstract val srg: RegularFileProperty
        @Internal get

    init {
        srg.convention(project.layout.file(project.provider { temporaryDir.resolve("mcp.srg") }))
    }

    @TaskAction
    fun generate() {
        val mappings = MemoryMappingTree()
        val srgMappings = IMappingBuilder.create()

        Tiny2FileReader.read(this.mappings.asFile.get().reader(), mappings)

        // Compiler doesn't like working with MemoryMappingTree for some reason
        val treeView: MappingTreeView = mappings

        val sourceNamespace = treeView.getNamespaceId(MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE)
        val targetNamespace =
            treeView.getNamespaceId(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

        for (type in treeView.classes) {
            val addedClass =
                srgMappings.addClass(type.getName(targetNamespace), type.getName(sourceNamespace))

            for (field in type.fields) {
                addedClass.field(field.getName(sourceNamespace), field.getName(targetNamespace))
            }

            for (method in type.methods) {
                addedClass.method(
                    method.getDesc(sourceNamespace),
                    method.getName(sourceNamespace),
                    method.getName(targetNamespace),
                )
            }
        }

        srgMappings.build().write(srg.getAsPath(), IMappingFile.Format.SRG)
    }
}
