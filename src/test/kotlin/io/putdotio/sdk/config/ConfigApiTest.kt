package io.putdotio.sdk.config

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigApiTest {
    @Test
    fun `get preserves every app-owned config value`() =
        withServer { server ->
            val rawConfig =
                """
                {
                  "playbackMode": "future-mode",
                  "recentTerms": ["one", "two words"],
                  "featureEnabled": false,
                  "futureConfig": {"nested": true}
                }
                """.trimIndent()
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "config": $rawConfig
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
                    assertEquals(Json.parseToJsonElement(rawConfig).jsonObject, sdk.appConfig.get().values)
                }
            }

            assertEquals("/v2/config", server.takeRequest().target)
        }

    @Test
    fun `save writes an app-owned key and JSON value`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.appConfig.save(AppConfigUpdate("consumerPreference", JsonPrimitive("value")))
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/config/consumerPreference", request.target)
            assertEquals("PUT", request.method)
            assertEquals("""{"value":"value"}""", request.body!!.utf8())
        }

    @Test
    fun `save encodes path-shaped config keys as one segment`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.appConfig.save(AppConfigUpdate("../account/info", JsonPrimitive("value")))
                }
            }

            assertEquals("/v2/config/..%2Faccount%2Finfo", server.takeRequest().target)
        }

    @Test
    fun `config update rejects blank keys`() {
        assertFailsWith<IllegalArgumentException> {
            AppConfigUpdate(" ", JsonPrimitive("value"))
        }
    }

    @Test
    fun `config update rejects dot path segment keys`() {
        assertFailsWith<IllegalArgumentException> {
            AppConfigUpdate("..", JsonPrimitive("value"))
        }
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
