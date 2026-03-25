package net.msrandom.minecraftcodev.mixins

import com.google.common.collect.HashMultimap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.msrandom.minecraftcodev.core.utils.SetMultimapSerializer
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

@CacheableTransform
abstract class StripMixins : TransformAction<StripMixins.Parameters> {
    abstract class Parameters : TransformParameters {
        abstract val appliedMixins: ConfigurableFileCollection
            @InputFiles
            @PathSensitive(PathSensitivity.NONE)
            get

        init {
            appliedMixins.convention()
        }
    }

    abstract val inputFile: Provider<FileSystemLocation>
        @InputArtifact
        @PathSensitive(PathSensitivity.NONE)
        get

    @OptIn(ExperimentalSerializationApi::class)
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

        val appliedMixins = HashMultimap.create<String, String>()
        val serializer = SetMultimapSerializer(String.serializer(), String.serializer())
        for (multimap in parameters.appliedMixins.map { Json.decodeFromStream(serializer, it.inputStream()) }) {
            appliedMixins.putAll(multimap)
        }

        val needStrip = zipFileSystem(input).use { fs ->
            val root = fs.getPath("/")
            handler.list(root).any { appliedMixins.containsKey(it) }
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
                    appliedMixins[path].takeIf { it.isNotEmpty() }?.let { it to path }
                }
                .forEach { (mixins, path) ->
                    val path = root.resolve(path)
                    path.writeLines(path.readLines().filterNot { line -> mixins.any { line.contains(it) } })
                }
            handler.remove(root)
        }
    }
}
