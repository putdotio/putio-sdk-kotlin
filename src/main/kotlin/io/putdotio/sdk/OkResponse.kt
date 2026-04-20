package io.putdotio.sdk

import kotlinx.serialization.Serializable

@Serializable
data class OkResponse(
    val status: String,
)

