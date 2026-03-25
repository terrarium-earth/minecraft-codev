package net.msrandom.minecraftcodev.forge.mappings

import net.fabricmc.mappingio.adapter.MappingNsCompleter
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.format.srg.TsrgFileReader
import net.fabricmc.mappingio.tree.MappingTreeView
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.srgutils.INamedMappingFile
import net.minecraftforge.srgutils.IRenamer
import net.msrandom.minecraftcodev.core.MappingsNamespace
import net.msrandom.minecraftcodev.core.getVersionList
import net.msrandom.minecraftcodev.core.resolve.MinecraftDownloadVariant
import net.msrandom.minecraftcodev.core.resolve.downloadMinecraftFile
import net.msrandom.minecraftcodev.forge.McpConfigFile
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin.Companion.SRG_MAPPINGS_NAMESPACE
import net.msrandom.minecraftcodev.remapper.MappingResolutionData
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.ZipMappingResolutionRule
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream

class McpConfigMappingResolutionRule : ZipMappingResolutionRule {
    override fun load(
        path: Path,
        fileSystem: FileSystem,
        isJar: Boolean,
        data: MappingResolutionData,
    ): Boolean {
        val mcpConfigFile = McpConfigFile.fromFile(path, fileSystem) ?: return false

        val mappings =
            if (mcpConfigFile.config.official) {
                val list = getVersionList(data.cacheDirectory, data.versionManifestUrl, data.isOffline)
                val version = list.version(mcpConfigFile.config.version)

                val clientMappings = downloadMinecraftFile(data.cacheDirectory, version, MinecraftDownloadVariant.ClientMappings, data.isOffline)!!.toFile()

                val joinedMappingsPath = fileSystem.getPath(mcpConfigFile.config.data.getValue("mappings")!!)
                val joinedMappings = joinedMappingsPath.inputStream().use(IMappingFile::load)
                val official = INamedMappingFile.load(clientMappings).getMap("right", "left")

                val isNeoforge = mcpConfigFile.config.spec == 4

                val classRenamer = object : IRenamer {
                    override fun rename(value: IMappingFile.IPackage) = official.remapPackage(value.original)
                    override fun rename(value: IMappingFile.IClass) = official.remapClass(value.original)

                    override fun rename(value: IMappingFile.IField) = if (isNeoforge) {
                        official.getClass(value.parent.original)?.remapField(value.original) ?: value.mapped
                    } else {
                        value.mapped
                    }

                    override fun rename(value: IMappingFile.IMethod) = if (isNeoforge) {
                        official.getClass(value.parent.original)?.remapMethod(value.original, value.descriptor) ?: value.mapped
                    } else {
                        value.mapped
                    }

                    override fun rename(value: IMappingFile.IParameter) = value.mapped
                }

                val mappingOutput = Files.createTempFile("mergedMappings", ".tsrg2")

                joinedMappings.rename(classRenamer).write(mappingOutput, IMappingFile.Format.TSRG2, false);

                mappingOutput
            } else {
                fileSystem.getPath(mcpConfigFile.config.data.getValue("mappings")!!)
            }

        val namedNamespace = data.visitor.tree.getNamespaceId(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

        val namespaceCompleter =
            if (namedNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
                MappingNsCompleter(
                    data.visitor.tree,
                    mapOf(
                        MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE to SRG_MAPPINGS_NAMESPACE,
                    ),
                    true,
                )
            } else {
                data.visitor.tree
            }

        val namespaceFixer =
            if (mcpConfigFile.config.official) {
                MappingNsRenamer(
                    namespaceCompleter,
                    mapOf("left" to MappingsNamespace.OBF, "right" to SRG_MAPPINGS_NAMESPACE),
                )
            } else {
                namespaceCompleter
            }

        mappings.inputStream().reader().use {
            TsrgFileReader.read(
                it,
                MappingsNamespace.OBF,
                SRG_MAPPINGS_NAMESPACE,
                namespaceFixer,
            )
        }

        return true
    }
}
