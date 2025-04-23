package net.msrandom.minecraftcodev.mixins

import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.mixins.mixin.GradleMixinRecorderExtension
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
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

@CacheableTransform
abstract class StripMixins : TransformAction<TransformParameters.None> {
    abstract val inputFile: Provider<FileSystemLocation>
        @InputArtifact
        @PathSensitive(PathSensitivity.NONE)
        get

    override fun transform(outputs: TransformOutputs) {
        val input = inputFile.get().toPath()

        val handler =
            zipFileSystem(input).use {
                val root = it.getPath("/")

                mixinListingRules.firstNotNullOfOrNull { rule ->
                    rule.load(root)
                }
            }

        if (handler == null) {
            outputs.file(inputFile)

            return
        }

        val needStrip = zipFileSystem(input).use { fs ->
            val root = fs.getPath("/")
            handler.list(root).any { GradleMixinRecorderExtension.CONFIG_TO_MIXINS.containsKey(it) }
        }

        if (!needStrip) {
            outputs.file(inputFile)

            return
        }

        val output = outputs.file("${input.nameWithoutExtension}-mixins-stripped.${input.extension}").toPath()

        input.copyTo(output, StandardCopyOption.COPY_ATTRIBUTES)

        zipFileSystem(output).use { it ->
            val root = it.getPath("/")
            handler.list(root)
                .mapNotNull { path ->
                    GradleMixinRecorderExtension.CONFIG_TO_MIXINS[path].takeIf { it.isNotEmpty() }?.let { it to path }
                }
                .forEach { (mixins, path) ->
                    val path = root.resolve(path)
                    path.writeLines(path.readLines().filterNot { line -> mixins.any { line.contains(it) } })
                }
            handler.remove(root)
        }
    }
}
