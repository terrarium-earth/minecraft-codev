package net.msrandom.minecraftcodev.remapper

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import net.msrandom.minecraftcodev.core.utils.hashFile
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path

const val REMAP_OPERATION_VERSION = 4

private fun compareHashBytes(a: HashCode, b: HashCode): Int {
    val bytesA = a.asBytes()
    val bytesB = b.asBytes()

    for ((x, y) in bytesA.zip(bytesB)) {
        val comparison = x.compareTo(y)

        if (comparison != 0) {
            return comparison
        }
    }

    return bytesA.size.compareTo(bytesB.size)
}

@Suppress("UnstableApiUsage")
internal fun computeRemapperId(
    mappingsPath: Path,
    classpathFiles: Iterable<File>,
    sourceNamespace: String,
    targetNamespace: String,
): String {
    val fileHashes = buildList {
        add(hashFile(mappingsPath))

        for (file in classpathFiles) {
            add(hashFile(file.toPath()))
        }
    }.sortedWith(::compareHashBytes)

    val cumulativeHasher = Hashing.murmur3_32_fixed().newHasher()

    for (hash in fileHashes) {
        cumulativeHasher.putBytes(hash.asBytes())
    }

    cumulativeHasher.putString(sourceNamespace, StandardCharsets.UTF_8)
    cumulativeHasher.putString(targetNamespace, StandardCharsets.UTF_8)

    return cumulativeHasher.hash().toString()
}
