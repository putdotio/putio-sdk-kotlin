package io.putdotio.sdk.account

import io.putdotio.sdk.core.PutioTransport
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AccountModelsTest {
    private val json: Json = PutioTransport.defaultJson

    @Test
    fun `toQueryMap includes only requested account info fields`() {
        val query =
            AccountInfoQuery(
                downloadToken = true,
                features = true,
                intercom = true,
                pas = true,
                platform = "android-tv",
                profitwell = true,
                pushToken = true,
            )

        assertEquals(
            mapOf(
                "download_token" to "1",
                "features" to "1",
                "intercom" to "1",
                "pas" to "1",
                "platform" to "android-tv",
                "profitwell" to "1",
                "push_token" to "1",
            ),
            query.toQueryMap(),
        )
    }

    @Test
    fun `account settings serializer encodes every supported update shape`() {
        assertEquals(
            """{"history_enabled":false}""",
            json.encodeToString(AccountSettingsUpdateSerializer, AccountSettingsPatch(false)),
        )
        assertEquals(
            """{"username":"new-name"}""",
            json.encodeToString(AccountSettingsUpdateSerializer, AccountUsernameUpdate(username = "new-name")),
        )
        assertEquals(
            """{"current_password":"secret","mail":"hello@put.io"}""",
            json.encodeToString(
                AccountSettingsUpdateSerializer,
                AccountMailUpdate(currentPassword = "secret", mail = "hello@put.io"),
            ),
        )
        assertEquals(
            """{"current_password":"secret","password":"new-secret"}""",
            json.encodeToString(
                AccountSettingsUpdateSerializer,
                AccountPasswordUpdate(currentPassword = "secret", password = "new-secret"),
            ),
        )
        assertEquals(
            """{"two_factor_enabled":{"code":"123456","enable":true}}""",
            json.encodeToString(
                AccountSettingsUpdateSerializer,
                AccountTwoFactorUpdate(
                    twoFactorEnabled = AccountTwoFactorSettings(code = "123456", enable = true),
                ),
            ),
        )
    }

    @Test
    fun `account settings serializer decodes every supported update shape`() {
        assertIs<AccountSettingsPatch>(
            json.decodeFromString(AccountSettingsUpdateSerializer, """{"hide_subtitles":true}"""),
        )
        assertIs<AccountUsernameUpdate>(
            json.decodeFromString(AccountSettingsUpdateSerializer, """{"username":"new-name"}"""),
        )
        assertIs<AccountMailUpdate>(
            json.decodeFromString(
                AccountSettingsUpdateSerializer,
                """{"current_password":"secret","mail":"hello@put.io"}""",
            ),
        )
        assertIs<AccountPasswordUpdate>(
            json.decodeFromString(
                AccountSettingsUpdateSerializer,
                """{"current_password":"secret","password":"new-secret"}""",
            ),
        )
        assertIs<AccountTwoFactorUpdate>(
            json.decodeFromString(
                AccountSettingsUpdateSerializer,
                """{"two_factor_enabled":{"code":"123456","enable":true}}""",
            ),
        )
    }

    @Test
    fun `account settings serializer rejects non object payloads`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(AccountSettingsUpdateSerializer, """["bad-shape"]""")
        }
    }

    @Test
    fun `account models fill sensible defaults when optional fields are omitted`() {
        val settings =
            json.decodeFromString(
                AccountSettings.serializer(),
                """
                {
                  "sort_by": "UPDATED_AT_DESC"
                }
                """.trimIndent(),
            )
        val info =
            json.decodeFromString(
                AccountInfo.serializer(),
                """
                {
                  "user_id": 7,
                  "username": "sdk-user",
                  "mail": "sdk@put.io",
                  "avatar_url": "https://static.put.io/avatar.png",
                  "account_status": "active",
                  "disk": {
                    "avail": 50,
                    "size": 75,
                    "used": 25
                  },
                  "settings": {
                    "sort_by": "UPDATED_AT_DESC"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(null, settings.tunnelRouteName)
        assertEquals(false, settings.nextEpisode)
        assertEquals(false, settings.historyEnabled)
        assertEquals(false, settings.hideSubtitles)
        assertEquals(true, settings.diagnosticsEnabled)
        assertEquals(false, settings.productAnalyticsEnabled)
        assertEquals(0L, info.trashSize)
        assertEquals(null, info.accountActive)
        assertEquals(null, info.downloadToken)
        assertEquals(emptyMap(), info.features)
        assertEquals(null, info.filesWillBeDeletedAt)
        assertEquals(null, info.userHash)
        assertEquals(false, info.isSubAccount)
    }
}
