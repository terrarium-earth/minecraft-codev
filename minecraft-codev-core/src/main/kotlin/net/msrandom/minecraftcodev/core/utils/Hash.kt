package net.msrandom.minecraftcodev.core.utils

import com.dynatrace.hash4j.file.FileHashing
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.internal.hash.HashCode
import java.nio.file.Path
import kotlin.io.path.inputStream

fun hashFile(file: Path) = FileHashing.imohash1_0_2().hashFileTo128Bits(file).asLong

suspend fun hashFileSuspend(file: Path) = withContext(Dispatchers.IO) { hashFile(file) }

@OptIn(ExperimentalStdlibApi::class)
fun checkHash(
    file: Path,
    expectedHash: String,
) = hashFile(file) == expectedHash.hexToLong()

@OptIn(ExperimentalStdlibApi::class)
suspend fun checkHashSuspend(
    file: Path,
    expectedHash: String,
) = hashFileSuspend(file) == expectedHash.hexToLong()

@Suppress("DEPRECATION", "UnstableApiUsage", "ControlFlowWithEmptyBody")
fun hashFileSha1(file: Path) = HashingInputStream(Hashing.sha1(), file.inputStream().buffered()).let {
    while(it.read() != -1);
    it.hash()
}

fun checkHashSha1(
    file: Path,
    expectedHash: String,
) = hashFileSha1(file) == HashCode.fromString(expectedHash)

suspend fun checkHashSha1Suspend(
    file: Path,
    expectedHash: String,
) = withContext(Dispatchers.IO) { checkHashSha1(file, expectedHash) }