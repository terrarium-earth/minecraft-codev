package net.msrandom.minecraftcodev.forge.task

import net.fabricmc.classtweaker.api.ClassTweakerReader
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor
import net.msrandom.minecraftcodev.accesswidener.isAccessWidenerFile
import org.cadixdev.at.AccessChange
import org.cadixdev.at.AccessTransform
import org.cadixdev.at.AccessTransformSet
import org.cadixdev.at.ModifierChange
import org.cadixdev.at.io.AccessTransformFormats
import org.cadixdev.bombe.type.signature.MethodSignature
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

@CacheableTask
abstract class GenerateAccessTransformer : DefaultTask() {
    abstract val input: ConfigurableFileCollection
        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        get

    abstract val output: RegularFileProperty
        @OutputFile
        get

    init {
        apply {
            output.convention(
                project.layout.dir(project.provider { temporaryDir }).map { it.file("accesstransformer.cfg") })
        }
    }

    @TaskAction
    fun generate() {
        val accessTransformers = AccessTransformSet.create()

        for (accessWidener in input) {
            if (!accessWidener.isAccessWidenerFile()) {
                // Implies that this is supposed to have specific handling, for example mod Jars to enable transitive Access Wideners in
                continue
            }

            val reader = ClassTweakerReader.create(object : ClassTweakerVisitor {
                override fun visitAccessWidener(owner: String) = object : AccessWidenerVisitor {
                    private fun getAccess(access: AccessWidenerVisitor.AccessType) =
                        when (access) {
                            AccessWidenerVisitor.AccessType.ACCESSIBLE -> AccessTransform.of(AccessChange.PUBLIC)
                            AccessWidenerVisitor.AccessType.MUTABLE,
                            AccessWidenerVisitor.AccessType.EXTENDABLE ->
                                AccessTransform.of(AccessChange.PUBLIC, ModifierChange.REMOVE)
                        }

                    override fun visitClass(
                        access: AccessWidenerVisitor.AccessType,
                        transitive: Boolean,
                    ) {
                        accessTransformers.getOrCreateClass(owner).merge(getAccess(access))
                    }

                    override fun visitMethod(
                        name: String,
                        descriptor: String,
                        access: AccessWidenerVisitor.AccessType,
                        transitive: Boolean,
                    ) {
                        accessTransformers.getOrCreateClass(owner)
                            .mergeMethod(MethodSignature.of(name, descriptor), getAccess(access))
                    }

                    override fun visitField(
                        name: String,
                        descriptor: String,
                        access: AccessWidenerVisitor.AccessType,
                        transitive: Boolean,
                    ) {
                        accessTransformers.getOrCreateClass(owner).mergeField(name, getAccess(access))
                    }
                }
            })

            accessWidener.bufferedReader().use(reader::read)
        }

        output.get().asFile.bufferedWriter().use {
            AccessTransformFormats.FML.write(it, accessTransformers)
        }
    }
}
