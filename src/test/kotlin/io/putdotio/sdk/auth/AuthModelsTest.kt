package io.putdotio.sdk.auth

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        val generated = json.decodeFromString(
            GenerateTotpEnvelope.serializer(),
            """
            {
              "status": "OK",
              "secret": "secret-123",
              "uri": "otpauth://totp/putio",
              "recovery_codes": {
                "created_at": "2026-04-23T10:00:00Z",
                "codes": [{"code": "rc-1", "used_at": null}]
              }
            }
            """.trimIndent(),
        )
        val verified = json.decodeFromString(
            VerifyTotpEnvelope.serializer(),
            """{"status":"OK","token":"verified-token","user_id":42}""",
        )
        val recoveryCodes = json.decodeFromString(
            RecoveryCodesEnvelope.serializer(),
            """
            {
              "status": "OK",
              "recovery_codes": {
                "created_at": "2026-04-23T10:00:00Z",
                "codes": [{"code": "rc-2", "used_at": "2026-04-23T11:00:00Z"}]
              }
            }
            """.trimIndent(),
        )
        val authorizationCode = AuthorizationCode(code = codeEnvelope.code, qrCodeUrl = codeEnvelope.qrCodeUrl)
        val generatedResult = GenerateTotpResult(
            recoveryCodes = generated.recoveryCodes,
            secret = generated.secret,
            uri = generated.uri,
        )
        val verifiedResult = VerifyTotpResult(token = verified.token, userId = verified.userId)

        assertEquals("ABCD", codeEnvelope.code)
        assertEquals("token-123", matchEnvelope.oauthToken)
        assertEquals(true, validation.result)
        assertEquals(5L, validation.tokenId)
        assertEquals("default", validation.tokenScope)
        assertEquals(42L, validation.userId)
        assertEquals("https://example.com/qr.png", authorizationCode.qrCodeUrl)
        assertEquals("secret-123", generatedResult.secret)
        assertEquals("rc-1", generatedResult.recoveryCodes.codes.first().code)
        assertEquals("verified-token", verifiedResult.token)
        assertEquals(42L, verifiedResult.userId)
        assertEquals("rc-2", recoveryCodes.recoveryCodes.codes.first().code)
        assertEquals("2026-04-23T11:00:00Z", recoveryCodes.recoveryCodes.codes.first().usedAt)
    }

    @Test
    fun `token validation keeps nullable backend fields nullable`() {
        val nullFields = json.decodeFromString(
            ValidateTokenResult.serializer(),
            """{"result":false,"token_id":null,"token_scope":null,"user_id":null}""",
        )
        val missingFields = json.decodeFromString(
            ValidateTokenResult.serializer(),
            """{"result":false}""",
        )

        assertEquals(false, nullFields.result)
        assertNull(nullFields.tokenId)
        assertNull(nullFields.tokenScope)
        assertNull(nullFields.userId)
        assertEquals(false, missingFields.result)
        assertNull(missingFields.tokenId)
        assertNull(missingFields.tokenScope)
        assertNull(missingFields.userId)
    }
}
