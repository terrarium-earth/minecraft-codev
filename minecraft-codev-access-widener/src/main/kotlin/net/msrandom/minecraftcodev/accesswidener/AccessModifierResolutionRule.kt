package net.msrandom.minecraftcodev.accesswidener

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.fabricmc.classtweaker.api.ClassTweakerReader
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor
import net.msrandom.minecraftcodev.core.ResolutionData
import net.msrandom.minecraftcodev.core.ResolutionRule
import net.msrandom.minecraftcodev.core.ZipResolutionRule
import net.msrandom.minecraftcodev.core.ZipResolutionRuleHandler
import net.msrandom.minecraftcodev.core.utils.serviceLoader
import org.gradle.api.file.FileCollection
import java.io.File
import java.nio.file.Path
import kotlin.io.path.inputStream

val ACCESS_WIDENER_EXTENSIONS = setOf("accesswidener", "ct")

fun File.isAccessWidenerFile(): Boolean =
    extension.isAccessWidenerExtension()

fun String.isAccessWidenerExtension(): Boolean =
    lowercase() in ACCESS_WIDENER_EXTENSIONS

fun mapAccessWidenerNamespace(visitor: ClassTweakerVisitor, source: String, target: String) =
    object : ClassTweakerVisitor {
        override fun visitHeader(namespace: String?) {
            val newNamespace = if (namespace == source) {
                target
            } else {
                namespace
            }

            visitor.visitHeader(newNamespace)
        }

        override fun visitAccessWidener(owner: String): AccessWidenerVisitor {
            val awVisitor = visitor.visitAccessWidener(owner) ?: return object : AccessWidenerVisitor {}

            return object : AccessWidenerVisitor {
                override fun visitClass(access: AccessWidenerVisitor.AccessType, transitive: Boolean) =
                    awVisitor.visitClass(access, transitive)

                override fun visitMethod(
                    name: String,
                    descriptor: String,
                    access: AccessWidenerVisitor.AccessType,
                    transitive: Boolean
                ) = awVisitor.visitMethod(name, descriptor, access, transitive)

                override fun visitField(
                    name: String,
                    descriptor: String?,
                    access: AccessWidenerVisitor.AccessType,
                    transitive: Boolean
                ) = awVisitor.visitField(name, descriptor, access, transitive)
            }
        }
    }

class AccessModifierResolutionData(
    visitor: AccessModifiers,
    val namespace: String?,
) : ResolutionData<AccessModifiers>(visitor)

interface AccessModifierResolutionRule : ResolutionRule<AccessModifierResolutionData>

interface ZipAccessModifierResolutionRule : ZipResolutionRule<AccessModifierResolutionData>

class ZipAccessModifierResolutionRuleHandler :
    ZipResolutionRuleHandler<AccessModifierResolutionData, ZipAccessModifierResolutionRule>(serviceLoader()),
    AccessModifierResolutionRule

class AccessWidenerResolutionRule : AccessModifierResolutionRule {
    override fun load(path: Path, extension: String, data: AccessModifierResolutionData): Boolean {
        if (!extension.isAccessWidenerExtension()) {
            return false
        }

        val reader = ClassTweakerReader.create(data.visitor)

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
    val widener = AccessModifiers(false, namespace, namedSource)

    val data = AccessModifierResolutionData(widener, namespace)

    for (file in files) {
        for (rule in accessModifierResolutionRules) {
            if (rule.load(file.toPath(), file.extension, data)) {
                break
            }
        }
    }

    return widener
}
