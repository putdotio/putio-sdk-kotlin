package io.putdotio.sdk.auth

import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.core.PutioAuth
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class AuthApi internal constructor(
    private val transport: PutioTransport,
) {
    fun buildLoginUrl(
        redirectUri: String,
        state: String,
        responseType: String = "token",
    ): String {
        val clientId = transport.config.clientId
            ?: throw PutioConfigurationException("PutioConfig.clientId is required to build the auth URL")

        return transport.buildUrl(
            path = "/authenticate",
            baseUrl = transport.config.webAppUrl,
            query = buildMap {
                put("client_id", clientId)
                transport.config.clientName?.let { put("client_name", it) }
                put("isolated", "1")
                put("redirect_uri", redirectUri)
                put("response_type", responseType)
                put("state", state)
            },
        )
    }

    suspend fun getCode(): AuthorizationCode {
        val clientId = transport.config.clientId
            ?: throw PutioConfigurationException("PutioConfig.clientId is required to request an auth code")

        val envelope =
            putioOperation(GET_CODE_ERROR_SPEC) {
                transport.get(
                    path = "/oauth2/oob/code",
                    serializer = AuthorizationCodeEnvelope.serializer(),
                    auth = PutioAuth.None,
                    query = buildMap {
                        put("app_id", clientId)
                        transport.config.clientName?.let { put("client_name", it) }
                    },
                )
            }

        return AuthorizationCode(
            code = envelope.code,
            qrCodeUrl = envelope.qrCodeUrl,
        )
    }

    suspend fun checkCodeMatch(code: String): String? =
        putioOperation(CHECK_CODE_MATCH_ERROR_SPEC) {
            transport.get(
                path = "/oauth2/oob/code/$code",
                serializer = CodeMatchEnvelope.serializer(),
                auth = PutioAuth.None,
            ).oauthToken
        }

    suspend fun validateToken(token: String? = null): ValidateTokenResult =
        putioOperation(VALIDATE_TOKEN_ERROR_SPEC) {
            transport.get(
                path = "/oauth2/validate",
                serializer = ValidateTokenResult.serializer(),
                auth = token?.let(PutioAuth::Token) ?: PutioAuth.ConfigToken,
            )
        }

    suspend fun generateTotp(): GenerateTotpResult =
        putioOperation(GENERATE_TOTP_ERROR_SPEC) {
            val envelope = transport.post(
                path = "/two_factor/generate/totp",
                serializer = GenerateTotpEnvelope.serializer(),
            )

            GenerateTotpResult(
                recoveryCodes = envelope.recoveryCodes,
                secret = envelope.secret,
                uri = envelope.uri,
            )
        }

    suspend fun verifyTotp(
        twoFactorScopedToken: String,
        code: String,
    ): VerifyTotpResult =
        putioOperation(VERIFY_TOTP_ERROR_SPEC) {
            val envelope = transport.post(
                path = "/two_factor/verify/totp",
                serializer = VerifyTotpEnvelope.serializer(),
                auth = PutioAuth.None,
                query = mapOf("oauth_token" to twoFactorScopedToken),
                form = mapOf("code" to code),
            )

            VerifyTotpResult(
                token = envelope.token,
                userId = envelope.userId,
            )
        }

    suspend fun getRecoveryCodes(): TwoFactorRecoveryCodes =
        putioOperation(GET_RECOVERY_CODES_ERROR_SPEC) {
            transport.get(
                path = "/two_factor/recovery_codes",
                serializer = RecoveryCodesEnvelope.serializer(),
            ).recoveryCodes
        }

    suspend fun regenerateRecoveryCodes(): TwoFactorRecoveryCodes =
        putioOperation(REGENERATE_RECOVERY_CODES_ERROR_SPEC) {
            transport.post(
                path = "/two_factor/recovery_codes/refresh",
                serializer = RecoveryCodesEnvelope.serializer(),
            ).recoveryCodes
        }
}

private val GET_CODE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "auth",
        operation = "getCode",
    )

private val CHECK_CODE_MATCH_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "auth",
        operation = "checkCodeMatch",
        knownErrors = listOf(PutioKnownErrorContract(statusCode = 404)),
    )

private val VALIDATE_TOKEN_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "auth",
        operation = "validateToken",
        knownErrors = listOf(
            PutioKnownErrorContract(statusCode = 401),
            PutioKnownErrorContract(statusCode = 403),
        ),
    )

private val GENERATE_TOTP_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "auth",
        operation = "generateTotp",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "already_exists", statusCode = 403),
            PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
        ),
    )

private val VERIFY_TOTP_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "auth",
        operation = "verifyTotp",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "invalid_setup", statusCode = 400),
            PutioKnownErrorContract(errorType = "invalid_code", statusCode = 400),
            PutioKnownErrorContract(errorType = "code_not_found", statusCode = 400),
            PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
        ),
    )

private val GET_RECOVERY_CODES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "auth",
        operation = "getRecoveryCodes",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "invalid_setup", statusCode = 400),
            PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
        ),
    )

private val REGENERATE_RECOVERY_CODES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "auth",
        operation = "regenerateRecoveryCodes",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "invalid_setup", statusCode = 400),
            PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
        ),
    )
