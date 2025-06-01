package net.msrandom.minecraftcodev.core.utils

import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import java.nio.file.Path
import com.google.common.io.Files as GoogleFiles

private fun hashFile(file: Path, function: HashFunction): HashCode = GoogleFiles.asByteSource(file.toFile()).hash(function)

fun hashFile(file: Path) = hashFile(file, Hashing.murmur3_32_fixed())

@Suppress("DEPRECATION")
fun hashFileSha1(file: Path) = hashFile(file, Hashing.sha1())

fun checkHashSha1(file: Path, expectedHash: String) = hashFileSha1(file) == HashCode.fromString(expectedHash)
