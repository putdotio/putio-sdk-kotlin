package io.putdotio.sdk.account

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountApiTest {
    @Test
    fun `getInfo decodes account payload and sends token auth`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
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

    @Test
    fun `getSettings decodes settings envelope`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "settings": {
                            "sort_by": "NAME_ASC",
                            "tunnel_route_name": "eu-west",
                            "next_episode": true,
                            "start_from": true,
                            "history_enabled": true,
                            "trash_enabled": true,
                            "show_optimistic_usage": false,
                            "two_factor_enabled": true,
                            "hide_subtitles": true,
                            "dont_autoselect_subtitles": false
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
                    val settings = sdk.account.getSettings()

                    assertEquals("NAME_ASC", settings.sortBy)
                    assertEquals("eu-west", settings.tunnelRouteName)
                    assertEquals(true, settings.twoFactorEnabled)
                    assertEquals(true, settings.hideSubtitles)
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/account/settings", request.target)
        }

    @Test
    fun `getInfo wraps failures with account operation context`() =
        withServer { server ->
            server.enqueue(apiErrorResponse(statusCode = 503, errorType = "ACCOUNT_UNAVAILABLE"))

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                accessToken = "secret-token",
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.account.getInfo()
                        }
                    }
                }

            assertEquals("account", error.domain)
            assertEquals("getInfo", error.operation)
            assertNull(error.contract)
            assertIs<PutioApiException>(error.underlyingError)
        }

    @Test
    fun `getSettings wraps failures with account operation context`() =
        withServer { server ->
            server.enqueue(apiErrorResponse(statusCode = 503, errorType = "ACCOUNT_UNAVAILABLE"))

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                accessToken = "secret-token",
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.account.getSettings()
                        }
                    }
                }

            assertEquals("account", error.domain)
            assertEquals("getSettings", error.operation)
            assertNull(error.contract)
            assertIs<PutioApiException>(error.underlyingError)
        }

    @Test
    fun `clearData posts typed destructive options`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "secret-token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.account.clearData(
                        AccountClearOptions(
                            files = true,
                            finishedTransfers = true,
                            activeTransfers = false,
                            rssFeeds = true,
                            rssLogs = false,
                            history = true,
                            trash = false,
                            friends = true,
                        ),
                    )
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/account/clear", request.target)
            assertEquals(
                """{"files":true,"finished_transfers":true,"rss_feeds":true,"history":true,"friends":true}""",
                request.body!!.utf8(),
            )
        }

    @Test
    fun `destroy posts current password as json`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "secret-token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.account.destroy(currentPassword = "secret-123")
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/account/destroy", request.target)
            assertEquals("""{"current_password":"secret-123"}""", request.body!!.utf8())
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private fun apiErrorResponse(
        statusCode: Int,
        errorType: String,
    ): MockResponse =
        MockResponse
            .Builder()
            .code(statusCode)
            .body(
                """
                {
                  "message": "account request failed",
                  "status_code": $statusCode,
                  "error_type": "$errorType"
                }
                """.trimIndent(),
            ).build()
}
