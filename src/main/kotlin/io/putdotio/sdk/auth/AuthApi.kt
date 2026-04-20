package io.putdotio.sdk.auth

import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.core.PutioAuth
import io.putdotio.sdk.core.PutioTransport

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

        val envelope = transport.get(
            path = "/oauth2/oob/code",
            serializer = AuthorizationCodeEnvelope.serializer(),
            auth = PutioAuth.None,
            query = buildMap {
                put("app_id", clientId)
                transport.config.clientName?.let { put("client_name", it) }
            },
        )

        return AuthorizationCode(
            code = envelope.code,
            qrCodeUrl = envelope.qrCodeUrl,
        )
    }

    suspend fun checkCodeMatch(code: String): String? =
        transport.get(
            path = "/oauth2/oob/code/$code",
            serializer = CodeMatchEnvelope.serializer(),
            auth = PutioAuth.None,
        ).oauthToken

    suspend fun validateToken(token: String? = null): ValidateTokenResult =
        transport.get(
            path = "/oauth2/validate",
            serializer = ValidateTokenResult.serializer(),
            auth = token?.let(PutioAuth::Token) ?: PutioAuth.ConfigToken,
        )
}
