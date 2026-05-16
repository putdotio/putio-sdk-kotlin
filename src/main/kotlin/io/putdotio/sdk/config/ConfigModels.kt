package io.putdotio.sdk.config

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable(with = ChromecastPlaybackType.Serializer::class)
@JvmInline
value class ChromecastPlaybackType(val raw: String) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val HLS = ChromecastPlaybackType("hls")
        val MP4 = ChromecastPlaybackType("mp4")

        private val knownValues = setOf(HLS, MP4)

        fun fromRaw(raw: String): ChromecastPlaybackType =
            when (raw) {
                HLS.raw -> HLS
                MP4.raw -> MP4
                else -> ChromecastPlaybackType(raw)
            }
    }

    object Serializer : RawStringValueSerializer<ChromecastPlaybackType>("ChromecastPlaybackType") {
        override fun fromRaw(raw: String): ChromecastPlaybackType = Companion.fromRaw(raw)

        override fun toRaw(value: ChromecastPlaybackType): String = value.raw
    }
}

@Serializable
data class UserConfig(
    @SerialName("chromecast_playback_type") val chromecastPlaybackType: ChromecastPlaybackType = ChromecastPlaybackType.HLS,
)

data class UserConfigUpdate(
    val key: String,
    val value: JsonElement,
) {
    init {
        require(key.isNotBlank()) { "Config key must not be blank" }
        require(key != "." && key != "..") { "Config key must not be a dot path segment" }
    }

    companion object {
        fun chromecastPlaybackType(playbackType: ChromecastPlaybackType): UserConfigUpdate =
            UserConfigUpdate(
                key = "chromecast_playback_type",
                value = JsonPrimitive(playbackType.raw),
            )
    }
}

@Serializable
internal data class UserConfigEnvelope(
    val config: UserConfig,
    val status: String,
)

@Serializable
internal data class ConfigValueUpdateBody(
    val value: JsonElement,
)
