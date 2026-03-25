package net.msrandom.minecraftcodev.core.utils

import com.google.common.collect.Multimaps
import com.google.common.collect.SetMultimap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.descriptors.setSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Suppress("UnstableApiUsage")
@OptIn(ExperimentalSerializationApi::class)
class SetMultimapSerializer<K, V>(
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<V>,
    private val valueFactory: () -> MutableSet<V> = { HashSet() }
) : KSerializer<SetMultimap<K, V>> {
    override val descriptor =
        SerialDescriptor(
            "Multimap",
            mapSerialDescriptor(
                keySerializer.descriptor, setSerialDescriptor(valueSerializer.descriptor)
            )
        )

    override fun serialize(encoder: Encoder, value: SetMultimap<K, V>) {
        MapSerializer(keySerializer, SetSerializer(valueSerializer)).serialize(encoder, Multimaps.asMap(value))
    }

    override fun deserialize(decoder: Decoder): SetMultimap<K, V> {
        val map = Multimaps.newSetMultimap<K, V>(HashMap(), valueFactory)
        for (entry in MapSerializer(keySerializer, SetSerializer(valueSerializer)).deserialize(decoder)) {
            map.putAll(entry.key, entry.value)
        }
        return map
    }
}