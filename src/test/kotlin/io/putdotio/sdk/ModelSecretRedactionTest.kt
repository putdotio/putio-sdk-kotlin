package io.putdotio.sdk

import io.putdotio.sdk.account.AccountMailUpdate
import io.putdotio.sdk.account.AccountPasswordUpdate
import io.putdotio.sdk.account.AccountTwoFactorSettings
import io.putdotio.sdk.account.AccountTwoFactorUpdate
import io.putdotio.sdk.auth.GenerateTotpResult
import io.putdotio.sdk.auth.TwoFactorRecoveryCode
import io.putdotio.sdk.auth.TwoFactorRecoveryCodes
import io.putdotio.sdk.auth.VerifyTotpResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ModelSecretRedactionTest {
    @Test
    fun `credential models redact secret fields in toString and keep non-secret metadata`() {
        val recoveryCode =
            TwoFactorRecoveryCode(
                code = "canary-recovery-code",
                usedAt = "2026-04-23T10:00:00Z",
            )
        val recoveryCodes =
            TwoFactorRecoveryCodes(
                codes = listOf(TwoFactorRecoveryCode(code = "canary-nested-recovery-code")),
                createdAt = "2026-04-23T09:00:00Z",
            )
        val config =
            PutioConfig(
                accessToken = "canary-access-token",
                clientId = "client-42",
                clientName = "put.io Android",
                baseUrl = "https://api.example/v2/",
            )
        val nullTokenConfig = PutioConfig(accessToken = null, clientId = "client-42")
        val generateTotp =
            GenerateTotpResult(
                recoveryCodes = recoveryCodes,
                secret = "canary-totp-secret",
                uri = "otpauth://totp/putio?secret=canary-uri-secret",
            )
        val verifyTotp =
            VerifyTotpResult(
                token = "canary-verify-token",
                userId = 42L,
            )
        val twoFactorSettings =
            AccountTwoFactorSettings(
                code = "canary-2fa-code",
                enable = true,
            )
        val twoFactorUpdate = AccountTwoFactorUpdate(twoFactorEnabled = twoFactorSettings)
        val mailUpdate =
            AccountMailUpdate(
                currentPassword = "canary-mail-password",
                mail = "user@example.com",
            )
        val passwordUpdate =
            AccountPasswordUpdate(
                currentPassword = "canary-current-password",
                password = "canary-new-password",
            )

        val cases =
            listOf(
                config.toString() to listOf("canary-access-token"),
                recoveryCode.toString() to listOf("canary-recovery-code"),
                recoveryCodes.toString() to listOf("canary-nested-recovery-code"),
                generateTotp.toString() to
                    listOf("canary-nested-recovery-code", "canary-totp-secret", "canary-uri-secret"),
                verifyTotp.toString() to listOf("canary-verify-token"),
                twoFactorSettings.toString() to listOf("canary-2fa-code"),
                twoFactorUpdate.toString() to listOf("canary-2fa-code"),
                mailUpdate.toString() to listOf("canary-mail-password"),
                passwordUpdate.toString() to listOf("canary-current-password", "canary-new-password"),
            )

        cases.forEach { (rendered, secrets) ->
            assertTrue(rendered.contains("<redacted>"), "Expected redaction marker in: $rendered")
            secrets.forEach { secret ->
                assertFalse(rendered.contains(secret), "Secret '$secret' leaked in: $rendered")
            }
        }

        assertTrue(config.toString().contains("clientId=client-42"))
        assertTrue(config.toString().contains("baseUrl=https://api.example/v2/"))
        assertTrue(nullTokenConfig.toString().contains("accessToken=null"))
        assertFalse(nullTokenConfig.toString().contains("<redacted>"))
        assertTrue(recoveryCode.toString().contains("usedAt=2026-04-23T10:00:00Z"))
        assertTrue(verifyTotp.toString().contains("userId=42"))
        assertTrue(twoFactorSettings.toString().contains("enable=true"))
        assertTrue(mailUpdate.toString().contains("mail=user@example.com"))
    }

    @Test
    fun `credential data classes preserve equality hashCode and copy`() {
        val original = PutioConfig(accessToken = "secret-1", clientId = "c1")
        val same = original.copy()
        val different = original.copy(accessToken = "secret-2")
        assertEquals(original, same)
        assertEquals(original.hashCode(), same.hashCode())
        assertNotEquals(original, different)

        val pwOriginal = AccountPasswordUpdate(currentPassword = "old", password = "new")
        assertEquals(pwOriginal, pwOriginal.copy())
        assertEquals(pwOriginal.hashCode(), pwOriginal.copy().hashCode())
        assertNotEquals(pwOriginal, pwOriginal.copy(password = "other"))
    }
}
