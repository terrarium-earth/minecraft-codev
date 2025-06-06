package net.msrandom.minecraftcodev.includes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.msrandom.minecraftcodev.core.ListedFileHandler
import net.msrandom.minecraftcodev.core.utils.hashFile
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists

@CacheableTransform
abstract class ExtractIncludes : TransformAction<TransformParameters.None> {
    abstract val inputFile: Provider<FileSystemLocation>
        @InputArtifact
        @PathSensitive(PathSensitivity.NONE)
        get

    abstract val classpath: FileCollection
        @Classpath
        @InputArtifactDependencies
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

            val inputHashes = runBlocking(Dispatchers.IO) {
                classpath
                    .map { async { hashFile(it.toPath()) } }
                    .awaitAll()
                    .toSet()
            }

            for (includedJar in handler.list(root)) {
                val path = fileSystem.getPath(includedJar)
                val hash = hashFile(path)

                if (hash !in inputHashes) {
                    val includeOutput = outputs.file(path.fileName.toString()).toPath()

                    println("Extracting $path from $input to $includeOutput")

                    path.copyTo(
                        includeOutput,
                        StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } else {
                    println("Skipping extracting $path from $input because hash $hash is in dependencies")
                }
            }
        } finally {
            fileSystem.close()
        }

        val output = outputs.file(input.fileName.toString()).toPath()

        input.copyTo(output, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)

        zipFileSystem(output).use { fs ->
            val root = fs.getPath("/")

            for (jar in handler!!.list(root)) {
                fs.getPath(jar).deleteIfExists()
            }

            handler.remove(root)
        }
    }
}
