package net.msrandom.minecraftcodev.core.utils

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.jetbrains.annotations.Blocking
import java.nio.file.*
import java.nio.file.spi.FileSystemProvider
import kotlin.streams.asSequence

fun zipFileSystem(
    path: Path,
    create: Boolean = false,
): FileSystem {
    val env = mapOf("create" to create.toString())

    for (provider in FileSystemProvider.installedProviders()) {
        try {
            return provider.newFileSystem(path, env)
        } catch (_: UnsupportedOperationException) {
        }
    }

    throw ProviderNotFoundException("Provider not found")
}

fun <T> Path.walk(action: Sequence<Path>.() -> T) =
    Files.walk(this).use {
        it.asSequence().action()
    }

fun FileSystemLocation.toPath(): Path = asFile.toPath()
fun FileSystemLocationProperty<*>.getAsPath(): Path = asFile.get().toPath()
