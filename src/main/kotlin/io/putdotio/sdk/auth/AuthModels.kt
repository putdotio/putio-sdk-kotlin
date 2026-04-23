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

@Serializable
data class TwoFactorRecoveryCode(
    val code: String,
    @SerialName("used_at") val usedAt: String? = null,
)

@Serializable
data class TwoFactorRecoveryCodes(
    val codes: List<TwoFactorRecoveryCode> = emptyList(),
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class GenerateTotpEnvelope(
    @SerialName("recovery_codes") val recoveryCodes: TwoFactorRecoveryCodes,
    val secret: String,
    val status: String,
    val uri: String,
)

@Serializable
data class GenerateTotpResult(
    val recoveryCodes: TwoFactorRecoveryCodes,
    val secret: String,
    val uri: String,
)

@Serializable
internal data class VerifyTotpEnvelope(
    val status: String,
    val token: String,
    @SerialName("user_id") val userId: Long,
)

@Serializable
data class VerifyTotpResult(
    val token: String,
    val userId: Long,
)

@Serializable
internal data class RecoveryCodesEnvelope(
    @SerialName("recovery_codes") val recoveryCodes: TwoFactorRecoveryCodes,
    val status: String,
)
