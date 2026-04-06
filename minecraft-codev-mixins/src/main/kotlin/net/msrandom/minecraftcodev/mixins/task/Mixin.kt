package net.msrandom.minecraftcodev.mixins.task

import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.msrandom.minecraftcodev.core.utils.SetMultimapSerializer
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.mixins.mixin.GradleMixinService
import net.msrandom.minecraftcodev.mixins.mixin.IsolatingMixinClassLoader
import net.msrandom.minecraftcodev.mixins.mixin.MappingIoRemapperAdapter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.net.URL

@CacheableTask
abstract class Mixin : DefaultTask() {

    abstract val inputFile: RegularFileProperty
        @InputFile
        @Classpath
        get

    abstract val mixinFiles: ConfigurableFileCollection
        @InputFiles
        @Classpath
        get

    abstract val classpath: ConfigurableFileCollection
        @InputFiles
        @CompileClasspath
        get

    abstract val side: Property<Side>
        @Input get

    abstract val mappings: RegularFileProperty
        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        get

    abstract val sourceNamespace: Property<String>
        @Input get

    abstract val targetNamespace: Property<String>
        @Input get

    abstract val outputFile: RegularFileProperty
        @OutputFile get

    abstract val appliedMixins: RegularFileProperty
        @OutputFile get

    init {
        outputFile.convention(
            project.layout.file(
                inputFile.map {
                    temporaryDir.resolve("${it.asFile.nameWithoutExtension}-with-mixins.${it.asFile.extension}")
                },
            ),
        )

        appliedMixins.convention(
            project.layout.file(
                inputFile.map {
                    temporaryDir.resolve("${it.asFile.nameWithoutExtension}-applied-mixins.json")
                },
            ),
        )

        side.convention(Side.UNKNOWN)
    }

    @TaskAction
    fun mixin() {
        // Target classpath URLs come first (so target's mixin deps are preferred)
        val targetUrls = (classpath.files + mixinFiles.files + listOf(inputFile.asFile.get()))
            .map { it.toURI().toURL() }

        // Plugin JARs as fallback
        val pluginUrls = collectIsolatedClasspathUrls()

        // Target first, then plugin fallback
        val allUrls = (targetUrls + pluginUrls).distinct()

        val isolatedClassLoader = IsolatingMixinClassLoader(
            allUrls.toTypedArray(),
            javaClass.classLoader
        )

        isolatedClassLoader.use { isolatedClassLoader ->
            // Load IsolatedMixinExecutor in the isolated classloader
            val executorClass = isolatedClassLoader.loadClass(
                "net.msrandom.minecraftcodev.mixins.mixin.IsolatedMixinExecutor"
            )
            val executor = executorClass.getDeclaredConstructor().newInstance()

            // Get the execute method
            val executeMethod = executorClass.getDeclaredMethod(
                "execute",
                String::class.java,           // inputFilePath
                String::class.java,           // outputFilePath
                List::class.java,             // mixinFilePaths
                List::class.java,             // classpathPaths
                String::class.java,           // side
                String::class.java,           // mappingsFilePath
                String::class.java,           // sourceNamespace
                String::class.java,           // targetNamespace
                String::class.java            // appliedMixinsFilePath
            )

            // Prepare parameters (all String/List<String> for cross-classloader safety)
            val inputFilePath = inputFile.getAsPath().toAbsolutePath().toString()
            val outputFilePath = outputFile.getAsPath().toAbsolutePath().toString()
            val mixinFilePaths = mixinFiles.files.map { it.absolutePath }
            val classpathPaths = classpath.files.map { it.absolutePath }
            val sideStr = side.get().name
            val mappingsFilePath = mappings.asFile.get().absolutePath
            val sourceNs = sourceNamespace.get()
            val targetNs = targetNamespace.get()
            val appliedMixinsFilePath = appliedMixins.asFile.get().absolutePath

            // Invoke execute method
            executeMethod.invoke(
                executor,
                inputFilePath,
                outputFilePath,
                mixinFilePaths,
                classpathPaths,
                sideStr,
                mappingsFilePath,
                sourceNs,
                targetNs,
                appliedMixinsFilePath
            )
        }
    }

    /**
     * Collect URLs of JARs containing classes that need to be isolated.
     * These include Mixin framework, ASM, mapping-io, and our mixin service classes.
     */
    private fun collectIsolatedClasspathUrls(): List<URL> {
        val keyClasses = listOf(
            // Our plugin classes
            GradleMixinService::class.java,
            MappingIoRemapperAdapter::class.java,
            // minecraft-codev-core classes
            SetMultimapSerializer::class.java,
            // Mixin library
            MixinEnvironment::class.java,
            MixinBootstrap::class.java,
            // Mixin Extras
            Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap"),
            // ASM
            ClassReader::class.java,
            ClassNode::class.java,
            // mapping-io
            MemoryMappingTree::class.java,
        )
        return keyClasses.mapNotNull {
            it.protectionDomain?.codeSource?.location
        }.distinct()
    }
}
