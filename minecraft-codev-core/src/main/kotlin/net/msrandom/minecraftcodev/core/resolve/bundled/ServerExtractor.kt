package net.msrandom.minecraftcodev.core.resolve.bundled

import net.msrandom.minecraftcodev.core.ModuleLibraryIdentifier
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.readLines

object ServerExtractor {
    fun extract(
        version: String,
        newServer: Path,
        serverFs: FileSystem,
        librariesList: Path,
    ): List<String> {
        val versions =
            serverFs.getPath("META-INF/versions.list").readLines().associate {
                val parts = it.split('\t')
                parts[1] to parts[2]
            }

        serverFs.getPath(
            "META-INF/versions/${versions.getValue(version)}",
        ).copyTo(newServer, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)

        val libraries = librariesList.readLines().map { ModuleLibraryIdentifier.load(it.split('\t')[1]) }

        val classified = libraries.filter { it.classifier != null }.flatMap {
            listOf(it, ModuleLibraryIdentifier(it.group, it.module, it.version, null))
        }

        // TODO This is not really a good solution, but for some reason bundled servers list linux specific dependencies sometimes, which breaks on mac and windows
        return libraries.filterNot(classified::contains).map(ModuleLibraryIdentifier::toString)
    }
}
