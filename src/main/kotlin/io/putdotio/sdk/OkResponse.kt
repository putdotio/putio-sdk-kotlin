package io.putdotio.sdk

import kotlinx.serialization.Serializable

@Serializable
data class OkResponse @JvmOverloads constructor(
    val status: String,
    val cursor: String? = null,
    val skipped: Int? = null,
)
