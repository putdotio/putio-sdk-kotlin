package io.putdotio.sdk.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public data class AppConfig(
    public val values: Map<String, JsonElement>,
) {
    public operator fun get(key: String): JsonElement? = values[key]
}

public data class AppConfigUpdate(
    public val key: String,
    public val value: JsonElement,
) {
    init {
        requireConfigKey(key)
    }
}

internal fun requireConfigKey(key: String) {
    require(key.isNotBlank()) { "Config key must not be blank" }
    require(key != "." && key != "..") { "Config key must not be a dot path segment" }
}

@Serializable
internal data class AppConfigEnvelope(
    val config: Map<String, JsonElement>,
    val status: String,
)

@Serializable
internal data class ConfigValueUpdateBody(
    val value: JsonElement,
)
