package net.msrandom.minecraftcodev.forge

import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@CacheableTransform
abstract class RemoveNameMappingService : TransformAction<TransformParameters.None> {
    abstract val inputFile: Provider<FileSystemLocation>
        @InputArtifact
        @PathSensitive(PathSensitivity.NONE)
        get

    override fun transform(outputs: TransformOutputs) {
        val input = inputFile.get().toPath()

        println("Transforming $input")
        if (!input.nameWithoutExtension.startsWith("fmlloader")) return

        val needRemove =
            zipFileSystem(input).use {
                it.getPath("META-INF/services/cpw.mods.modlauncher.api.INameMappingService").deleteIfExists()
            }

        if (!needRemove) {
            println("Needn't transform $input")
            outputs.file(inputFile)
            return
        }

        val output = outputs.file("${input.nameWithoutExtension}-no-name-mapping.${input.extension}").toPath()

        input.copyTo(output, StandardCopyOption.COPY_ATTRIBUTES)
        println("Transformed $input $output")
    }
}