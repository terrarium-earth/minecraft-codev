package net.msrandom.minecraftcodev.core.utils

import com.dynatrace.hash4j.file.FileHashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

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
