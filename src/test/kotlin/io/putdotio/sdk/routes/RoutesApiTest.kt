package io.putdotio.sdk.routes

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesApiTest {
    @Test
    fun `list decodes tunnel routes`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "routes": [
                            {
                              "name": "EU",
                              "description": "Europe route",
                              "hosts": ["eu1.put.io", "eu2.put.io"]
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
                    val routes = sdk.routes.list()
                    assertEquals("EU", routes.first().name)
                    assertEquals(listOf("eu1.put.io", "eu2.put.io"), routes.first().hosts)
                }
            }

            assertEquals("/v2/tunnel/routes", server.takeRequest().target)
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
