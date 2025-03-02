package net.msrandom.minecraftcodev.core.utils

import com.google.common.hash.HashCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream

fun hashFile(file: Path): HashCode {
    val hash =
        file.inputStream().use { stream ->
            val sha1Hash = MessageDigest.getInstance("SHA-1")

            val buffer = ByteArray(8192)

            var read: Int

            while (stream.read(buffer).also { read = it } > 0) {
                sha1Hash.update(buffer, 0, read)
            }

            HashCode.fromBytes(sha1Hash.digest())
        }

    return hash
}

suspend fun hashFileSuspend(file: Path): HashCode {
    return withContext(Dispatchers.IO) {
        hashFile(file)
    }
}

fun checkHash(
    file: Path,
    expectedHash: String,
) = hashFile(file) == HashCode.fromString(expectedHash)

suspend fun checkHashSuspend(
    file: Path,
    expectedHash: String,
) = hashFileSuspend(file) == HashCode.fromString(expectedHash)
