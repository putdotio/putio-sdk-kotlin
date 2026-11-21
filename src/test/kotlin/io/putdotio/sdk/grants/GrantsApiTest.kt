package io.putdotio.sdk.grants

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioConfigurationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GrantsApiTest {
    @Test
    fun `list decodes authorized oauth grants`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "apps": [
                            {
                              "id": 7,
                              "name": "Android TV",
                              "description": "put.io TV app",
                              "website": "https://put.io",
                              "has_icon": true
                            }
                          ]
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
                    val grants = sdk.grants.list()
                    assertEquals(
                        listOf(
                            OAuthGrant(
                                id = 7,
                                name = "Android TV",
                                description = "put.io TV app",
                                website = "https://put.io",
                                hasIcon = true,
                            ),
                        ),
                        grants,
                    )
                }
            }

            assertEquals("/v2/oauth/grants", server.takeRequest().target)
        }

    @Test
    fun `revoke posts to grant delete endpoint`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.grants.revoke(id = 7)
                }
            }

            assertEquals("/v2/oauth/grants/7/delete", server.takeRequest().target)
        }

    @Test
    fun `revokeClient posts to the session delete endpoint`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.grants.revokeClient(id = 32)
                }
            }

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v2/oauth/clients/32/delete", request.target)
        }

    @Test
    fun `clients decodes each session with its app and last use`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "clients": [
                            {
                              "id": 31,
                              "created_at": "2026-11-01T09:00:00",
                              "app_id": 7,
                              "note": null,
                              "last_used_at": "2026-11-09T12:30:00",
                              "user_agent": "okhttp",
                              "client_name": "Pixel 9",
                              "ip_address": "10.0.0.x",
                              "active": true,
                              "app_name": "Android TV"
                            },
                            {
                              "id": 32,
                              "created_at": "2026-10-01T09:00:00",
                              "app_id": 8,
                              "note": null,
                              "last_used_at": null,
                              "user_agent": "curl",
                              "client_name": "",
                              "ip_address": "10.0.0.x",
                              "active": false
                            }
                          ]
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
                    val clients = sdk.grants.clients()
                    assertEquals(
                        listOf(
                            OAuthClient(
                                id = 31,
                                appId = 7,
                                appName = "Android TV",
                                clientName = "Pixel 9",
                                createdAt = "2026-11-01T09:00:00",
                                lastUsedAt = "2026-11-09T12:30:00",
                                active = true,
                            ),
                            OAuthClient(
                                id = 32,
                                appId = 8,
                                createdAt = "2026-10-01T09:00:00",
                            ),
                        ),
                        clients,
                    )
                }
            }

            assertEquals("/v2/oauth/clients", server.takeRequest().target)
        }

    @Test
    fun `buildIconRequest sends the current access token as a header on a token-free url`() {
        PutioClient(PutioConfig(accessToken = "access", baseUrl = "https://example.test/v2/")).use { sdk ->
            val request = sdk.grants.buildIconRequest(appId = 7)
            assertEquals("https://example.test/v2/oauth/apps/7/icon", request.url)
            assertEquals(mapOf("Authorization" to "Token access"), request.headers)
            assertFalse(request.toString().contains("access"))

            sdk.setAccessToken("rotated")
            assertEquals(mapOf("Authorization" to "Token rotated"), sdk.grants.buildIconRequest(7).headers)

            sdk.clearAccessToken()
            assertFailsWith<PutioConfigurationException> { sdk.grants.buildIconRequest(7) }
        }
    }

    @Test
    fun `linkDevice posts oob code and decodes linked app`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "app": {
                            "id": 8,
                            "name": "Linked Device",
                            "description": ""
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
                    val app = sdk.grants.linkDevice("PUTIO1")
                    assertEquals(8L, app.id)
                    assertEquals("Linked Device", app.name)
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/oauth2/oob/code", request.target)
            assertEquals("code=PUTIO1", request.body!!.utf8())
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
