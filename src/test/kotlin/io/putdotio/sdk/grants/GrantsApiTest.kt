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
    fun `list decodes authorized oauth grants`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
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
    fun `revoke posts to grant delete endpoint`() = withServer { server ->
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
    fun `linkDevice posts oob code and decodes linked app`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
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
