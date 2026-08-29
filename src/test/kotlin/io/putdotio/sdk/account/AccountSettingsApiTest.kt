package io.putdotio.sdk.account

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioOperationErrorReason
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AccountSettingsApiTest {
    @Test
    fun `saveSettings sends json payload`() =
        withServer { server ->
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

    @Test
    fun `saveSettings maps known account errors with typed operation context`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(400)
                    .body(
                        """
                        {
                          "message": "current password is invalid",
                          "status_code": 400,
                          "error_type": "INVALID_CURRENT_PASSWORD"
                        }
                        """.trimIndent(),
                    ).build(),
            )

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                accessToken = "token",
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.account.saveSettings(
                                AccountPasswordUpdate(
                                    currentPassword = "wrong-password",
                                    password = "new-password",
                                ),
                            )
                        }
                    }
                }

            assertEquals("account", error.domain)
            assertEquals("saveSettings", error.operation)
            assertEquals("INVALID_CURRENT_PASSWORD", error.contract?.errorType)
            val reason = assertIs<PutioOperationErrorReason.ErrorType>(error.reason)
            assertEquals("INVALID_CURRENT_PASSWORD", reason.errorType)
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
