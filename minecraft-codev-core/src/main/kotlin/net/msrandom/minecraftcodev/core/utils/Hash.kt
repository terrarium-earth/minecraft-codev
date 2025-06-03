package net.msrandom.minecraftcodev.core.utils

import com.google.common.hash.Funnels
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.ByteStreams
import java.nio.file.Path
import kotlin.io.path.inputStream

@Suppress("UnstableApiUsage")
private fun hashFile(file: Path, function: HashFunction): HashCode {
    val hasher = function.newHasher()

    file.inputStream().use {
        ByteStreams.copy(it, Funnels.asOutputStream(hasher));
    }

    return hasher.hash();
}

fun hashFile(file: Path) = hashFile(file, Hashing.murmur3_32_fixed())

@Suppress("DEPRECATION")
fun hashFileSha1(file: Path) = hashFile(file, Hashing.sha1())

fun checkHashSha1(file: Path, expectedHash: String) = hashFileSha1(file) == HashCode.fromString(expectedHash)
