package net.msrandom.minecraftcodev.forge

import arrow.core.Either
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

inline fun <reified T> Json.decodeOrNull(stream: InputStream): T? = safeDecode<T>(stream).getOrNull()

inline fun <reified T> Json.safeDecode(stream: InputStream) = Either.catchOrThrow<SerializationException, T> {
    decodeFromStream(stream)
}
