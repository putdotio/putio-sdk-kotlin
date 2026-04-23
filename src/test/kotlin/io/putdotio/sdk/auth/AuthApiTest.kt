package io.putdotio.sdk.auth

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class AuthApiTest {
    @Test
    fun `buildLoginUrl uses web app base and client metadata`() {
        val sdk = PutioClient(
            PutioConfig(
                clientId = "android-app",
                clientName = "put.io Android",
                webAppUrl = "https://app.put.io/",
            ),
        )

        val url = sdk.auth.buildLoginUrl(
            redirectUri = "putio://auth/callback",
            state = "android-state",
        )

        assertEquals(
            "https://app.put.io/authenticate?client_id=android-app&client_name=put.io%20Android&isolated=1&redirect_uri=putio%3A%2F%2Fauth%2Fcallback&response_type=token&state=android-state",
            url,
        )
    }

    @Test
    fun `validateToken can use an explicit token override`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
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
    fun `getCode sends public auth query without authorization`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
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
    fun `checkCodeMatch returns null when oauth token is absent`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
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
    fun `checkCodeMatch wraps api errors with auth operation context`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder()
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
                )
                .build(),
        )

        val error = assertFailsWith<PutioOperationException> {
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
    fun `buildLoginUrl requires clientId`() {
        val sdk = PutioClient(PutioConfig())

        val error = assertFailsWith<PutioConfigurationException> {
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
