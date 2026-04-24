package io.putdotio.sdk.account

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class AccountSettingsApiTest {
    @Test
    fun `saveSettings sends json payload`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.account.saveSettings(
                    AccountSettingsPatch(
                        historyEnabled = false,
                        hideSubtitles = true,
                        sortBy = "NAME_ASC",
                    ),
                )
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/account/settings", request.target)
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals("""{"history_enabled":false,"hide_subtitles":true,"sort_by":"NAME_ASC"}""", request.body!!.utf8())
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
