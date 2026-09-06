package io.putdotio.sdk

import kotlinx.serialization.Serializable

@Serializable
data class OkResponse
    @JvmOverloads
    constructor(
        val status: String,
        val cursor: String? = null,
        val skipped: Int? = null,
    ) {
        init {
            require(status == "OK") { "Acknowledgement status must be OK" }
        }
    }
