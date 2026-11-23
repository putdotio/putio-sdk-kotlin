package io.putdotio.sdk

import kotlinx.serialization.Serializable

@Serializable
public data class OkResponse
    @JvmOverloads
    public constructor(
        public val status: String,
        public val cursor: String? = null,
        public val skipped: Int? = null,
    ) {
        init {
            require(status == "OK") { "Acknowledgement status must be OK" }
        }
    }
