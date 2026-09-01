package io.putdotio.sdk.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

data class AppConfig(
    val values: Map<String, JsonElement>,
) {
    operator fun get(key: String): JsonElement? = values[key]
}

data class AppConfigUpdate(
    val key: String,
    val value: JsonElement,
) {
    init {
        require(key.isNotBlank()) { "Config key must not be blank" }
        require(key != "." && key != "..") { "Config key must not be a dot path segment" }
    }
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
