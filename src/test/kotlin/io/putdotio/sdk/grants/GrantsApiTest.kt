package io.putdotio.sdk.grants

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

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
                    assertEquals(7L, grants.first().id)
                    assertEquals("Android TV", grants.first().name)
                    assertEquals(true, grants.first().hasIcon)
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
    fun `buildIconUrl points at the app icon and prints redacted`() {
        PutioClient(PutioConfig(accessToken = "token")).use { sdk ->
            val url = sdk.grants.buildIconUrl(appId = 7, accessToken = "secret-token")
            assertEquals("https://api.put.io/v2/oauth/apps/7/icon?oauth_token=secret-token", url.value)
            assertEquals(false, url.toString().contains("secret-token"))
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
