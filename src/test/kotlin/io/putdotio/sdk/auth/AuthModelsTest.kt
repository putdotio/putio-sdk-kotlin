package io.putdotio.sdk.auth

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `auth envelopes and validation results decode expected fields`() {
        val codeEnvelope = json.decodeFromString(
            AuthorizationCodeEnvelope.serializer(),
            """{"status":"OK","code":"ABCD","qr_code_url":"https://example.com/qr.png"}""",
        )
        val matchEnvelope = json.decodeFromString(
            CodeMatchEnvelope.serializer(),
            """{"status":"OK","oauth_token":"token-123"}""",
        )
        val validation = json.decodeFromString(
            ValidateTokenResult.serializer(),
            """{"result":true,"token_id":5,"token_scope":"default","user_id":42}""",
        )
        val authorizationCode = AuthorizationCode(code = codeEnvelope.code, qrCodeUrl = codeEnvelope.qrCodeUrl)

        assertEquals("ABCD", codeEnvelope.code)
        assertEquals("token-123", matchEnvelope.oauthToken)
        assertEquals(true, validation.result)
        assertEquals(5L, validation.tokenId)
        assertEquals("default", validation.tokenScope)
        assertEquals(42L, validation.userId)
        assertEquals("https://example.com/qr.png", authorizationCode.qrCodeUrl)
    }
}
