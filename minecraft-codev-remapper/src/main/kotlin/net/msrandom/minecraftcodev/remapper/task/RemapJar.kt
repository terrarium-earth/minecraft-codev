package net.msrandom.minecraftcodev.remapper.task

import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectoryProvider
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.remapper.MappingService
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin

abstract class RemapJar : Jar() {
    abstract val input: RegularFileProperty
        @InputFile
        get

    abstract val sourceNamespace: Property<String>
        @Input get

    abstract val targetNamespace: Property<String>
        @Input get

    abstract val mappings: RegularFileProperty
        @InputFile
        get

    abstract val classpath: ConfigurableFileCollection
        @InputFiles
        get

    abstract val remappedClasses: DirectoryProperty
        @Internal get

    abstract val cacheDirectory: DirectoryProperty
        @Internal get

    @get:ServiceReference(MinecraftCodevRemapperPlugin.MAPPING_SERVICE_NAME)
    abstract val mappingService: Property<MappingService>

    abstract val remapperId: Property<String>
        @Input get

    init {
        group = LifecycleBasePlugin.BUILD_GROUP

        sourceNamespace.convention(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)

        cacheDirectory.set(getGlobalCacheDirectoryProvider(project))
        remappedClasses.set(temporaryDir.resolve("remapped"))

        from(remappedClasses)

        doFirst {
            this as RemapJar

            val inputPath = input.getAsPath()
            val outputPath = archiveFile.get().toPath()
            val mappingsPath = mappings.getAsPath()

            val service = mappingService.get()

            service.remap(
                remapperId = remapperId.get(),
                input = inputPath,
                output = outputPath,
                mappingsPath = mappingsPath,
                classpathFiles = classpath.files,
                sourceNamespace = sourceNamespace.get(),
                targetNamespace = targetNamespace.get(),
            ).join()
        }
    }
}
