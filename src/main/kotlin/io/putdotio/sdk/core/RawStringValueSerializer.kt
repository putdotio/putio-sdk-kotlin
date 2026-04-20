package io.putdotio.sdk.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class RawStringValueSerializer<T>(
    serialName: String,
) : KSerializer<T> {
    final override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    final override fun deserialize(decoder: Decoder): T = fromRaw(decoder.decodeString())

    final override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        encoder.encodeString(toRaw(value))
    }

    protected abstract fun fromRaw(raw: String): T

    protected abstract fun toRaw(value: T): String
}
