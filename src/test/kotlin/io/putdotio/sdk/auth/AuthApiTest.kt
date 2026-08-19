package io.putdotio.sdk.auth

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthApiTest {
    @Test
    fun `buildLoginUrl uses web app base and client metadata`() {
        val sdk =
            PutioClient(
                PutioConfig(
                    clientId = "android-app",
                    clientName = "put.io Android",
                    webAppUrl = "https://app.put.io/",
                ),
            )

        val url =
            sdk.auth.buildLoginUrl(
                redirectUri = "putio://auth/callback",
                state = "android-state",
            )

        assertEquals(
            "https://app.put.io/authenticate?client_id=android-app&client_name=put.io%20Android&isolated=1&redirect_uri=putio%3A%2F%2Fauth%2Fcallback&response_type=token&state=android-state",
            url,
        )
    }

    @Test
    fun `validateToken can use an explicit token override`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "result": true,
                          "token_id": 5,
                          "token_scope": "default",
                          "user_id": 42
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val result = sdk.auth.validateToken("override-token")
                    assertEquals(true, result.result)
                    assertEquals(42L, result.userId)
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/oauth2/validate", request.target)
            assertEquals("Token override-token", request.headers["Authorization"])
        }

    @Test
    fun `logout posts to grants logout endpoint`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.auth.logout()
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/oauth/grants/logout", request.target)
            assertEquals("Token token", request.headers["Authorization"])
        }

    @Test
    fun `generateTotp and recovery code flows decode typed envelopes`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "secret": "secret-123",
                          "uri": "otpauth://totp/putio",
                          "recovery_codes": {
                            "created_at": "2026-04-23T10:00:00Z",
                            "codes": [
                              {"code": "rc-1", "used_at": null},
                              {"code": "rc-2", "used_at": "2026-04-23T11:00:00Z"}
                            ]
                          }
                        }
                        """.trimIndent(),
                    ).build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "recovery_codes": {
                            "created_at": "2026-04-23T12:00:00Z",
                            "codes": [{"code": "rc-3", "used_at": null}]
                          }
                        }
                        """.trimIndent(),
                    ).build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "recovery_codes": {
                            "created_at": "2026-04-23T13:00:00Z",
                            "codes": [{"code": "rc-4", "used_at": null}]
                          }
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val generated = sdk.auth.generateTotp()
                    val recoveryCodes = sdk.auth.getRecoveryCodes()
                    val refreshed = sdk.auth.regenerateRecoveryCodes()

                    assertEquals("secret-123", generated.secret)
                    assertEquals("otpauth://totp/putio", generated.uri)
                    assertEquals(
                        "rc-1",
                        generated.recoveryCodes.codes
                            .first()
                            .code,
                    )
                    assertEquals("2026-04-23T11:00:00Z", generated.recoveryCodes.codes[1].usedAt)
                    assertEquals("rc-3", recoveryCodes.codes.first().code)
                    assertEquals("rc-4", refreshed.codes.first().code)
                }
            }

            val generateRequest = server.takeRequest()
            val listRequest = server.takeRequest()
            val refreshRequest = server.takeRequest()
            assertEquals("/v2/two_factor/generate/totp", generateRequest.target)
            assertEquals("/v2/two_factor/recovery_codes", listRequest.target)
            assertEquals("/v2/two_factor/recovery_codes/refresh", refreshRequest.target)
            assertEquals("Token token", generateRequest.headers["Authorization"])
            assertEquals("Token token", listRequest.headers["Authorization"])
            assertEquals("Token token", refreshRequest.headers["Authorization"])
        }

    @Test
    fun `verifyTotp sends scoped token query and code form without configured auth`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "token": "verified-token",
                          "user_id": 42
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val result =
                        sdk.auth.verifyTotp(
                            twoFactorScopedToken = "two-factor-token",
                            code = "123456",
                        )
                    assertEquals("verified-token", result.token)
                    assertEquals(42L, result.userId)
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/two_factor/verify/totp?oauth_token=two-factor-token", request.target)
            assertEquals("code=123456", request.body!!.utf8())
            assertEquals(null, request.headers["Authorization"])
        }

    @Test
    fun `getCode sends public auth query without authorization`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "code": "ABCD",
                          "qr_code_url": "https://example.com/qr.png"
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        clientId = "android-app",
                        clientName = "put.io TV",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val result = sdk.auth.getCode()
                    assertEquals("ABCD", result.code)
                    assertEquals("https://example.com/qr.png", result.qrCodeUrl)
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/oauth2/oob/code?app_id=android-app&client_name=put.io%20TV", request.target)
            assertEquals(null, request.headers["Authorization"])
        }

    @Test
    fun `checkCodeMatch returns null when oauth token is absent`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "oauth_token": null
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    assertNull(sdk.auth.checkCodeMatch("ABCD"))
                }
            }
        }

    @Test
    fun `checkCodeMatch wraps api errors with auth operation context`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(404)
                    .body(
                        """
                        {
                          "status": "ERROR",
                          "status_code": 404,
                          "error_type": "NOT_FOUND",
                          "message": "code not found"
                        }
                        """.trimIndent(),
                    ).build(),
            )

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.auth.checkCodeMatch("ABCD")
                        }
                    }
                }

            assertEquals("auth", error.domain)
            assertEquals("checkCodeMatch", error.operation)
            val underlying = assertIs<PutioApiException>(error.underlyingError)
            assertEquals(404, underlying.statusCode)
        }

    @Test
    fun `verifyTotp wraps invalid code errors with auth operation context`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(400)
                    .body(
                        """
                        {
                          "status": "ERROR",
                          "status_code": 400,
                          "error_type": "code_not_found",
                          "message": "Invalid TOTP code."
                        }
                        """.trimIndent(),
                    ).build(),
            )

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.auth.verifyTotp(
                                twoFactorScopedToken = "two-factor-token",
                                code = "000000",
                            )
                        }
                    }
                }

            assertEquals("auth", error.domain)
            assertEquals("verifyTotp", error.operation)
            val underlying = assertIs<PutioApiException>(error.underlyingError)
            assertEquals(400, underlying.statusCode)
            assertEquals("code_not_found", underlying.errorType)
        }

    @Test
    fun `buildLoginUrl requires clientId`() {
        val sdk = PutioClient(PutioConfig())

        val error =
            assertFailsWith<PutioConfigurationException> {
                sdk.auth.buildLoginUrl(
                    redirectUri = "putio://auth/callback",
                    state = "android-state",
                )
            }

        assertEquals("PutioConfig.clientId is required to build the auth URL", error.message)
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
