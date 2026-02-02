package net.msrandom.minecraftcodev.accesswidener

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerVisitor
import net.msrandom.minecraftcodev.core.ResolutionData
import net.msrandom.minecraftcodev.core.ResolutionRule
import net.msrandom.minecraftcodev.core.ZipResolutionRule
import net.msrandom.minecraftcodev.core.ZipResolutionRuleHandler
import net.msrandom.minecraftcodev.core.utils.serviceLoader
import org.gradle.api.file.FileCollection
import java.nio.file.Path
import kotlin.io.path.inputStream

class AccessModifierResolutionData(
    visitor: AccessModifiers,
    val namespace: String?,
    val namedSource: Boolean,
) : ResolutionData<AccessModifiers>(visitor)

interface AccessModifierResolutionRule : ResolutionRule<AccessModifierResolutionData>

interface ZipAccessModifierResolutionRule : ZipResolutionRule<AccessModifierResolutionData>

class ZipAccessModifierResolutionRuleHandler :
    ZipResolutionRuleHandler<AccessModifierResolutionData, ZipAccessModifierResolutionRule>(serviceLoader()),
    AccessModifierResolutionRule

fun mapAccessWidenerNamespace(visitor: AccessWidenerVisitor, source: String, target: String) = object : AccessWidenerVisitor {
    override fun visitHeader(namespace: String) {
        val newNamespace = if (namespace == source) {
            target
        } else {
            namespace
        }

        super.visitHeader(newNamespace)
    }

    override fun visitClass(
        name: String,
        access: AccessWidenerReader.AccessType,
        transitive: Boolean,
    ) {
        visitor.visitClass(name, access, transitive)
    }

    override fun visitMethod(
        owner: String,
        name: String,
        descriptor: String,
        access: AccessWidenerReader.AccessType,
        transitive: Boolean,
    ) {
        visitor.visitMethod(owner, name, descriptor, access, transitive)
    }

    override fun visitField(
        owner: String,
        name: String,
        descriptor: String,
        access: AccessWidenerReader.AccessType,
        transitive: Boolean,
    ) {
        visitor.visitField(owner, name, descriptor, access, transitive)
    }
}

class AccessWidenerResolutionRule : AccessModifierResolutionRule {
    override fun load(path: Path, extension: String, data: AccessModifierResolutionData): Boolean {
        if (extension.lowercase() != "accesswidener") {
            return false
        }

        val visitor = if (data.namedSource) {
            mapAccessWidenerNamespace(data.visitor, "official", "named")
        } else {
            data.visitor
        }

        val reader = AccessWidenerReader(visitor)

        path.inputStream().bufferedReader().use {
            reader.read(it, data.namespace)
        }

        return true
    }
}

class AccessModifierJsonResolutionRule : AccessModifierResolutionRule {
    override fun load(path: Path, extension: String, data: AccessModifierResolutionData): Boolean {
        if (extension.lowercase() != "json") {
            return false
        }

        val modifiers =
            path.inputStream().use {
                Json.decodeFromStream<AccessModifiers>(it)
            }

        data.visitor.visit(modifiers)

        return true
    }

}

private val accessModifierResolutionRules = serviceLoader<AccessModifierResolutionRule>()

internal fun loadAccessWideners(
    files: FileCollection,
    namespace: String?,
    namedSource: Boolean,
): AccessModifiers {
    val widener = AccessModifiers(false, namespace)

    val data = AccessModifierResolutionData(widener, namespace, namedSource)

    for (file in files) {
        for (rule in accessModifierResolutionRules) {
            if (rule.load(file.toPath(), file.extension, data)) {
                break
            }
        }
    }

    return widener
}
