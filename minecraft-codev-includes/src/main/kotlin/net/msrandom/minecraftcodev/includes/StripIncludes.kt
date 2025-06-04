package net.msrandom.minecraftcodev.includes

import net.msrandom.minecraftcodev.core.ListedFileHandler
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

@CacheableTransform
abstract class StripIncludes : TransformAction<TransformParameters.None> {
    abstract val inputFile: Provider<FileSystemLocation>
        @InputArtifact
        @PathSensitive(PathSensitivity.NONE)
        get

    override fun transform(outputs: TransformOutputs) {
        val input = inputFile.get().toPath()

        val fileSystem = zipFileSystem(input)

        val handler: ListedFileHandler?

        try {
            val root = fileSystem.getPath("/")

            handler =
                includedJarListingRules.firstNotNullOfOrNull { rule ->
                    rule.load(root)
                }

            if (handler == null) {
                outputs.file(inputFile)

                return
            }
        } finally {
            fileSystem.close()
        }

        val output = outputs.file(input.fileName.toString()).toPath()

        input.copyTo(output, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)

        zipFileSystem(output).use { fs ->
            val root = fs.getPath("/")

            for (jar in handler.list(root)) {
                fs.getPath(jar).deleteIfExists()
            }

            handler.remove(root)
        }
    }
}