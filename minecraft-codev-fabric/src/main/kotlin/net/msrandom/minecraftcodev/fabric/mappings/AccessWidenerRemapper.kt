package net.msrandom.minecraftcodev.fabric.mappings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerRemapper
import net.fabricmc.accesswidener.AccessWidenerWriter
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.tinyremapper.TinyRemapper
import net.msrandom.minecraftcodev.core.MappingsNamespace
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin.Companion.MOD_JSON
import net.msrandom.minecraftcodev.remapper.ExtraFileRemapper
import java.nio.file.FileSystem
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.writeText

class AccessWidenerRemapper : ExtraFileRemapper {
    override fun invoke(remapper: TinyRemapper, mappings: MappingTreeView, fileSystem: FileSystem, sourceNamespace: String, targetNamespace: String) {
        val modJson = fileSystem.getPath(MOD_JSON)

        if (modJson.notExists()) {
            return
        }

        val json =
            modJson.inputStream().use {
                json.decodeFromStream<JsonObject>(it)
            }

        val accessWidener = json["accessWidener"]?.jsonPrimitive?.contentOrNull?.let(fileSystem::getPath) ?: return

        if (accessWidener.notExists()) {
            return
        }

        val writer = AccessWidenerWriter()

        val reader =
            AccessWidenerReader(AccessWidenerRemapper(
                writer,
                remapper.environment.remapper,
                sourceNamespace,
                targetNamespace
            ))

        accessWidener.inputStream().bufferedReader().use {
            reader.read(it, sourceNamespace.takeUnless { it == MappingsNamespace.OBF } ?: "official")
        }

        accessWidener.writeText(writer.writeString())
    }
}
