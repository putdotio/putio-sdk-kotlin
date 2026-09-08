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
    fun `account models decode full info and settings payloads`() {
        val settings =
            json.decodeFromString(
                AccountSettings.serializer(),
                """
                {
                  "sort_by": "NAME_ASC",
                  "tunnel_route_name": "eu-west",
                  "next_episode": true,
                  "use_start_from": true,
                  "history_enabled": true,
                  "trash_enabled": true,
                  "show_optimistic_usage": false,
                  "two_factor_enabled": true,
                  "hide_subtitles": true,
                  "dont_autoselect_subtitles": false
                }
                """.trimIndent(),
            )
        val info =
            json.decodeFromString(
                AccountInfo.serializer(),
                """
                {
                  "user_id": 42,
                  "username": "altay",
                  "mail": "altay@put.io",
                  "avatar_url": "https://static.put.io/avatar.png",
                  "account_status": "active",
                  "trash_size": 12,
                  "account_active": true,
                  "download_token": "download-token",
                  "features": {
                    "beta": true
                  },
                  "files_will_be_deleted_at": "2026-05-01T10:00:00Z",
                  "password_last_changed_at": "2026-04-20T10:00:00Z",
                  "user_hash": "user-hash",
                  "disk": {
                    "avail": 90,
                    "size": 100,
                    "used": 10
                  },
                  "settings": {
                    "sort_by": "NAME_ASC",
                    "next_episode": true,
                    "use_start_from": true,
                    "history_enabled": true,
                    "trash_enabled": true,
                    "show_optimistic_usage": false,
                    "two_factor_enabled": false,
                    "hide_subtitles": false,
                    "dont_autoselect_subtitles": false
                  }
                }
                """.trimIndent(),
            )

        assertEquals("eu-west", settings.tunnelRouteName)
        assertEquals(true, settings.useStartFrom)
        @Suppress("DEPRECATION")
        assertEquals(settings.useStartFrom, settings.startFrom)
        assertEquals(true, settings.historyEnabled)
        assertEquals(42L, info.userId)
        assertEquals(90L, info.disk.available)
        assertEquals("download-token", info.downloadToken?.value)
        assertEquals("<redacted download token>", info.downloadToken.toString())
        assertEquals(false, info.toString().contains("download-token"))
        assertEquals(true, info.features["beta"])
        assertEquals("user-hash", info.userHash)
        assertEquals(true, info.settings.useStartFrom)
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
        assertEquals(true, settings.productAnalyticsEnabled)
        assertEquals(true, settings.supportWidgetEnabled)
        assertEquals(0L, info.trashSize)
        assertEquals(null, info.accountActive)
        assertEquals(null, info.downloadToken)
        assertEquals(emptyMap(), info.features)
        assertEquals(null, info.filesWillBeDeletedAt)
        assertEquals(null, info.userHash)
    }
}
