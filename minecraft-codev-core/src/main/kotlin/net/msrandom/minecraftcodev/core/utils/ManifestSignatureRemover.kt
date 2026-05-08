package net.msrandom.minecraftcodev.core.utils

import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream

// Mainly copied from tiny-remapper MetaInfFixer

private fun shouldStripForFixMeta(file: Path): Boolean {
    val fileName = file.fileName.toString()

    // https://docs.oracle.com/en/java/javase/12/docs/specs/jar/jar.html#signed-jar-file
    return fileName.endsWith(".SF")
            || fileName.endsWith(".DSA")
            || fileName.endsWith(".RSA")
            || fileName.endsWith(".EC")
            || fileName.startsWith("SIG-")
}

private fun fixManifest(manifest: Manifest) {
    val mainAttrs = manifest.mainAttributes

    mainAttrs.remove(Attributes.Name.SIGNATURE_VERSION)

    val iterator = manifest.entries.values.iterator()
    while (iterator.hasNext()) {
        val attributes = iterator.next()

        val keyIterator = attributes.keys.iterator()
        while (keyIterator.hasNext()) {
            val name = keyIterator.next().toString()

            if (name.endsWith("-Digest") || name.contains("-Digest-") || name == "Magic") {
                keyIterator.remove()
            }
        }

        if (attributes.isEmpty()) {
            iterator.remove()
        }
    }
}

fun stripManifestSignature(fileSystem: FileSystem) {
    val manifestPath = fileSystem.getPath(JarFile.MANIFEST_NAME)

    if (manifestPath.exists()) {
        val fixedManifest = manifestPath.inputStream()
            .use(::Manifest)
            .also(::fixManifest)

        manifestPath.outputStream().use(fixedManifest::write)
    }

    val metaInf = manifestPath.parent

    if (metaInf.exists()) {
        for (path in metaInf.listDirectoryEntries()) {
            if (shouldStripForFixMeta(path)) {
                path.deleteExisting()
            }
        }
    }
}
