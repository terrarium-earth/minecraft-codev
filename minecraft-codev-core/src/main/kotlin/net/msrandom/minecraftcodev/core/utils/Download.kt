package net.msrandom.minecraftcodev.core.utils

import com.google.common.hash.HashCode
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

private val logger = LoggerFactory.getLogger("Download")

private fun downloadTo(uri: URI, output: Path) {
    logger.info("Downloading $uri")

    output.createParentDirectories()

    uri.toURL().openStream().use { input ->
        output.outputStream().buffered().use { output ->
            input.copyTo(output)
        }
    }
}

fun download(
    uri: URI,
    sha1: String?,
    output: Path,
    isOffline: Boolean,
    alwaysRefresh: Boolean = false,
) {
    if (!output.exists()) {
        if (isOffline) {
            throw GradleException("Trying to download or access $uri in offline mode")
        }

        downloadTo(uri, output)
        return
    }

    val outputHash by lazy { hashFileSha1(output) }

    if (sha1 != null) {
        if (!alwaysRefresh && outputHash == HashCode.fromString(sha1)) {
            logger.debug("Using cached file {} since metadata checksum {} matches", output, sha1)
            return
        }

        if (isOffline) {
            throw GradleException(
                "Cached version of $uri at $output has mismatched hash $outputHash, expected $sha1, can not re-download in offline mode",
            )
        }

        downloadTo(uri, output)
        return
    }

    if (isOffline) {
        logger.debug("Using cached file {} without extra checksum validation due to being in offline mode", output)
        return
    }

    if (!alwaysRefresh) {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"

        val sha1Header = connection.getHeaderField("X-Checksum-Sha1")

        val hash = if (sha1Header != null) {
            sha1Header
        } else {
            val etag = connection.getHeaderField("ETag")

            if (etag != null && etag.startsWith("{SHA1{")) {
                etag.substring(6, etag.length - 2)
            } else {
                null
            }
        }

        if (hash != null && outputHash == HashCode.fromString(hash)) {
            logger.debug("Using cached file {} since server-reported checksum {} matches", output, hash)
            return
        }
    }

    downloadTo(uri, output)
}
