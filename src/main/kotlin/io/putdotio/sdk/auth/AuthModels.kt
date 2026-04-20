package io.putdotio.sdk.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AuthorizationCodeEnvelope(
    val code: String,
    @SerialName("qr_code_url") val qrCodeUrl: String,
    val status: String,
)

@Serializable
internal data class CodeMatchEnvelope(
    @SerialName("oauth_token") val oauthToken: String? = null,
    val status: String,
)

@Serializable
data class AuthorizationCode(
    val code: String,
    val qrCodeUrl: String,
)

@Serializable
data class ValidateTokenResult(
    val result: Boolean,
    @SerialName("token_id") val tokenId: Long? = null,
    @SerialName("token_scope") val tokenScope: String? = null,
    @SerialName("user_id") val userId: Long? = null,
)

