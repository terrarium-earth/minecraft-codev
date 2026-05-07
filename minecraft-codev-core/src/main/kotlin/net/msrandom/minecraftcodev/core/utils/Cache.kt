package net.msrandom.minecraftcodev.core.utils

import com.google.common.hash.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.jetbrains.annotations.Blocking
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.Thread.sleep
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.concurrent.withLock
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createLinkPointingTo
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.resolve

fun <R : Any> RepositoryResourceAccessor.withCachedResource(
    cacheDirectory: File,
    relativePath: String,
    reader: (InputStream) -> R
): R? {
    val path = cacheDirectory.resolve("metadata-rule-download-cache").resolve(relativePath)

    return if (path.exists()) {
        path.inputStream().use(reader)
    } else {
        var result: R? = null

        withResource(relativePath) {
            val stream = buffered()

            stream.mark(Int.MAX_VALUE)
            path.toPath().parent.createDirectories()
            stream.copyTo(path.outputStream())

            stream.reset()

            result = reader(stream)
        }

        result
    }
}

fun getGlobalCacheDirectoryProvider(project: Project): Provider<Directory> =
    project.layout.dir(project.provider { getGlobalCacheDirectory(project) })

fun getGlobalCacheDirectory(project: Project): File =
    project.gradle.gradleUserHomeDir.resolve("caches/minecraft-codev")

fun getLocalCacheDirectoryProvider(project: Project): Provider<Directory> =
    project.rootProject.layout.buildDirectory.dir("minecraft-codev")

private fun getVanillaExtractJarPath(
    cacheDirectory: Path,
    version: String,
    variant: String,
): Path =
    cacheDirectory
        .resolve("vanilla-extracts")
        .resolve("$variant-$version.${ArtifactTypeDefinition.JAR_TYPE}")

internal fun commonJarPath(
    cacheDirectory: Path,
    version: String,
) = getVanillaExtractJarPath(cacheDirectory, version, "common")

fun Path.tryLink(target: Path) {
    try {
        createLinkPointingTo(target)
    } catch (_: Exception) {
        target.copyTo(this, StandardCopyOption.COPY_ATTRIBUTES)
    }
}

internal fun clientJarPath(
    cacheDirectory: Path,
    version: String,
) = getVanillaExtractJarPath(cacheDirectory, version, "client")

@Blocking
private inline fun withLock(path: Path, action: () -> Unit) {
    FileChannel.open(path).use { channel ->
        while (true) {
            val lock = channel.tryLock()

            if (lock == null) {
                sleep(1000)
                continue
            }

            try {
                action()
            } finally {
                lock.release()
            }
        }
    }
}

@Suppress("UnstableApiUsage")
fun cacheExpensiveOperation(
    cacheDirectory: Path,
    operationName: String,
    cacheKey: Iterable<Path>,
    vararg outputPaths: Path,
    generate: (List<Path>) -> Unit,
) {
    val hashes = runBlocking(Dispatchers.IO) {
        coroutineScope {
            cacheKey.map {
                async {
                    hashFile(it)
                }
            }.awaitAll().toSortedSet { a, b ->
                val bytesA = a.asBytes()
                val bytesB = b.asBytes()

                for ((a, b) in bytesA.zip(bytesB)) {
                    val comparison = a.compareTo(b)

                    if (comparison != 0) {
                        return@toSortedSet comparison
                    }
                }

                bytesA.size.compareTo(bytesB.size)
            }
        }
    }

    val cumulativeHasher = Hashing.murmur3_32_fixed().newHasher()

    for (hash in hashes) {
        cumulativeHasher.putBytes(hash.asBytes())
    }

    val directoryName = cumulativeHasher.hash().toString()

    val cachedOperationDirectoryName = cacheDirectory
        .resolve("cached-operations")
        .resolve(operationName)
        .resolve(directoryName)

    withLock(cachedOperationDirectoryName.resolve("lock")) {
        val allCached = outputPaths.all {
            cachedOperationDirectoryName.resolve(it.name).exists()
        }

        if (allCached) {
            for (outputPath in outputPaths) {
                val cachedOutput = cachedOperationDirectoryName.resolve(outputPath.name)

                outputPath.deleteIfExists()
                outputPath.tryLink(cachedOutput)
            }

            println("Cache hit for $operationName operation for $outputPaths")

            return
        }

        println("Cache miss for $operationName operation for $outputPaths")

        val temporaryPaths = outputPaths.map { outputPath ->
            val temporaryPath =
                Files.createTempFile("$operationName-${outputPath.nameWithoutExtension}", ".${outputPath.extension}")
            temporaryPath.deleteExisting()

            temporaryPath
        }

        generate(temporaryPaths)

        cachedOperationDirectoryName.createDirectories()

        for ((temporaryPath, outputPath) in temporaryPaths.zip(outputPaths)) {
            val cachedOutput = cachedOperationDirectoryName.resolve(outputPath.name)

            if (temporaryPath.exists()) {
                temporaryPath.copyTo(
                    cachedOutput,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }

        for (outputPath in outputPaths) {
            val cachedOutput = cachedOperationDirectoryName.resolve(outputPath.name)

            outputPath.deleteIfExists()

            if (cachedOutput.exists()) {
                outputPath.tryLink(cachedOutput)
            }
        }
    }
}
