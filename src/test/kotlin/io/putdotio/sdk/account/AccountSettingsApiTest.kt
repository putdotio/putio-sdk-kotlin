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
                            useStartFrom = true,
                            historyEnabled = false,
                            hideSubtitles = true,
                            sortBy = "NAME_ASC",
                            diagnosticsEnabled = false,
                            productAnalyticsEnabled = false,
                            supportWidgetEnabled = true,
                        ),
                    )
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/account/settings", request.target)
            assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
            assertEquals(
                """{"history_enabled":false,"hide_subtitles":true,"sort_by":"NAME_ASC","use_start_from":true,""" +
                    """"diagnostics_enabled":false,"product_analytics_enabled":false,"support_widget_enabled":true}""",
                request.body!!.utf8(),
            )
        }

    @Test
    fun `saveSettings maps every known account error with typed operation context`() =
        withServer { server ->
            SAVE_SETTINGS_ERROR_CONTRACTS.forEach { (errorType, statusCode) ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(statusCode)
                        .body(
                            """{"message":"known error","status_code":$statusCode,"error_type":"$errorType"}""",
                        ).build(),
                )
            }

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    SAVE_SETTINGS_ERROR_CONTRACTS.forEach { (errorType, statusCode) ->
                        val error =
                            assertFailsWith<PutioOperationException> {
                                sdk.account.saveSettings(AccountSettingsPatch(historyEnabled = true))
                            }

                        assertEquals("account", error.domain)
                        assertEquals("saveSettings", error.operation)
                        assertEquals(errorType, error.contract?.errorType)
                        assertEquals(statusCode, error.contract?.statusCode)
                        val reason = assertIs<PutioOperationErrorReason.ErrorType>(error.reason)
                        assertEquals(errorType, reason.errorType)
                    }
                }
            }
        }

    @Test
    fun `saveSettings leaves unknown account errors unmatched`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(422)
                    .body(
                        """{"message":"unknown error","status_code":422,"error_type":"NEW_ERROR"}""",
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
                                AccountSettingsPatch(historyEnabled = true),
                            )
                        }
                    }
                }

            assertEquals("account", error.domain)
            assertEquals("saveSettings", error.operation)
            assertEquals(null, error.contract)
            assertEquals(null, error.reason)
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private companion object {
        val SAVE_SETTINGS_ERROR_CONTRACTS =
            listOf(
                "INVALID_CURRENT_PASSWORD" to 400,
                "INVALID_NEW_PASSWORD" to 400,
                "INVALID_USERNAME" to 400,
                "INVALID_MAIL" to 400,
                "DISPOSABLE_MAIL_NOT_ALLOWED" to 400,
                "INVALID_CALLBACK_URL" to 400,
                "INVALID_PUSHOVER_TOKEN" to 400,
                "INVALID_CODE" to 400,
                "INVALID_ENABLE" to 400,
                "INVALID_VALUE" to 400,
                "PWNED_NEW_PASSWORD" to 400,
                "SAME_USERNAME" to 409,
                "EXISTING_MAIL" to 409,
                "USERNAME_EXISTS" to 400,
                "UNAVAILABLE_VALUE" to 403,
                "ALREADY_ENABLED" to 403,
                "INVALID_STATE" to 403,
                "NOT_ENABLED" to 403,
            )
    }
}
