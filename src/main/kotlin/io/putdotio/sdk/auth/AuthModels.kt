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
public data class AuthorizationCode(
    public val code: String,
    public val qrCodeUrl: String,
)

@Serializable
public data class ValidateTokenResult(
    public val result: Boolean,
    @SerialName("token_id") public val tokenId: Long? = null,
    @SerialName("token_scope") public val tokenScope: String? = null,
    @SerialName("user_id") public val userId: Long? = null,
)

@Serializable
public data class TwoFactorRecoveryCode(
    public val code: String,
    @SerialName("used_at") public val usedAt: String? = null,
) {
    public override fun toString(): String = "TwoFactorRecoveryCode(code=<redacted>, usedAt=$usedAt)"
}

@Serializable
public data class TwoFactorRecoveryCodes(
    public val codes: List<TwoFactorRecoveryCode> = emptyList(),
    @SerialName("created_at") public val createdAt: String,
)

@Serializable
internal data class GenerateTotpEnvelope(
    @SerialName("recovery_codes") val recoveryCodes: TwoFactorRecoveryCodes,
    val secret: String,
    val status: String,
    val uri: String,
)

@Serializable
public data class GenerateTotpResult(
    public val recoveryCodes: TwoFactorRecoveryCodes,
    public val secret: String,
    public val uri: String,
) {
    public override fun toString(): String = "GenerateTotpResult(recoveryCodes=<redacted>, secret=<redacted>, uri=<redacted>)"
}

@Serializable
internal data class VerifyTotpEnvelope(
    val status: String,
    val token: String,
    @SerialName("user_id") val userId: Long,
)

@Serializable
public data class VerifyTotpResult(
    public val token: String,
    public val userId: Long,
) {
    public override fun toString(): String = "VerifyTotpResult(token=<redacted>, userId=$userId)"
}

@Serializable
internal data class RecoveryCodesEnvelope(
    @SerialName("recovery_codes") val recoveryCodes: TwoFactorRecoveryCodes,
    val status: String,
)
