package net.msrandom.minecraftcodev.fabric.task

import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerVisitor
import net.fabricmc.accesswidener.AccessWidenerWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import kotlin.io.path.bufferedWriter
import kotlin.io.path.deleteIfExists

private fun mapAccessWidenerNamespace(visitor: AccessWidenerVisitor, source: String, target: String) = object : AccessWidenerVisitor {
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

@CacheableTask
abstract class MergeAccessWideners : DefaultTask() {
    abstract val input: ConfigurableFileCollection
        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        get

    abstract val accessWidenerName: Property<String>
        @Input
        get

    abstract val output: RegularFileProperty
        @OutputFile
        get

    abstract val namedSource: Property<Boolean>
        @Optional
        @Input
        get

    init {
        apply {
            output.convention(
                project.layout.dir(project.provider { temporaryDir }).flatMap {
                    accessWidenerName.map { name ->
                        it.file("$name.accessWidener")
                    }
                },
            )
        }
    }

    @TaskAction
    fun generate() {
        val input = input.filter {
            // Different extensions imply that this is supposed to have specific handling, for example mod Jars to enable transitive Access Wideners in
            it.extension.lowercase() == "accesswidener"
        }

        val output = output.get().asFile.toPath()

        if (input.isEmpty) {
            output.deleteIfExists()
            return
        }

        output.bufferedWriter().use {
            val writer = AccessWidenerWriter()

            val visitor = if (namedSource.getOrElse(false)) {
                mapAccessWidenerNamespace(writer, "named", "official")
            } else {
                writer
            }

            val reader = AccessWidenerReader(visitor)

            for (accessWidener in input) {
                accessWidener.bufferedReader().use(reader::read)
            }

            it.write(writer.writeString())
        }
    }
}
