package io.putdotio.sdk.account

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class AccountApiTest {
    @Test
    fun `getInfo decodes account payload and sends token auth`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "info": {
                    "user_id": 42,
                    "username": "altay",
                    "mail": "altay@put.io",
                    "avatar_url": "https://static.put.io/avatar.png",
                    "account_status": "active",
                    "trash_size": 12,
                    "disk": {
                      "avail": 90,
                      "size": 100,
                      "used": 10
                    },
                    "settings": {
                      "sort_by": "NAME_ASC",
                      "next_episode": true,
                      "start_from": true,
                      "history_enabled": true,
                      "trash_enabled": true,
                      "show_optimistic_usage": false,
                      "two_factor_enabled": false,
                      "hide_subtitles": false,
                      "dont_autoselect_subtitles": false
                    }
                  }
                }
                """.trimIndent(),
            ).build(),
        )

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "secret-token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                val account = sdk.account.getInfo(AccountInfoQuery(downloadToken = true))

                assertEquals("altay", account.username)
                assertEquals(90L, account.disk.available)
                assertNotNull(account.settings)
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/account/info?download_token=1", request.target)
        assertEquals("Token secret-token", request.headers["Authorization"])
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
