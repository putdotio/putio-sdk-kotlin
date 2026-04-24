package io.putdotio.sdk.live

import io.putdotio.sdk.account.AccountSettingsPatch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountLiveTest {
    @Test
    fun `account info and settings decode from the live API`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val info = sdk.account.getInfo()
                val settings = sdk.account.getSettings()

                assertTrue(info.userId > 0)
                assertTrue(info.username.isNotBlank())
                assertEquals(info.settings.hideSubtitles, settings.hideSubtitles)
            }
        }
    }

    @Test
    fun `account settings patch is reversible`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val before = sdk.account.getSettings()
                val nextValue = !before.hideSubtitles

                try {
                    sdk.account.saveSettings(AccountSettingsPatch(hideSubtitles = nextValue))
                    val changed = sdk.account.getSettings()
                    assertEquals(nextValue, changed.hideSubtitles)
                } finally {
                    sdk.account.saveSettings(AccountSettingsPatch(hideSubtitles = before.hideSubtitles))
                    val restored = sdk.account.getSettings()
                    assertEquals(before.hideSubtitles, restored.hideSubtitles)
                }
            }
        }
    }
}
