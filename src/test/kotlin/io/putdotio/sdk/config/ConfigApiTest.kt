package io.putdotio.sdk.config

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ConfigApiTest {
    @Test
    fun `get decodes user config and preserves unknown playback types`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "config": {
                            "chromecast_playback_type": "future-mode"
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
                    val config = sdk.userConfig.get()
                    assertEquals("future-mode", config.chromecastPlaybackType.raw)
                    assertFalse(config.chromecastPlaybackType.isKnown)
                }
            }

            assertEquals("/v2/config", server.takeRequest().target)
        }

    @Test
    fun `setChromecastPlaybackType writes typed config key`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.userConfig.setChromecastPlaybackType(ChromecastPlaybackType.MP4)
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/config/chromecast_playback_type", request.target)
            assertEquals("PUT", request.method)
            assertEquals("""{"value":"mp4"}""", request.body!!.utf8())
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
                    sdk.userConfig.save(UserConfigUpdate("../account/info", JsonPrimitive("value")))
                }
            }

            assertEquals("/v2/config/..%2Faccount%2Finfo", server.takeRequest().target)
        }

    @Test
    fun `config update rejects blank keys`() {
        assertFailsWith<IllegalArgumentException> {
            UserConfigUpdate(" ", JsonPrimitive("value"))
        }
    }

    @Test
    fun `config update rejects dot path segment keys`() {
        assertFailsWith<IllegalArgumentException> {
            UserConfigUpdate("..", JsonPrimitive("value"))
        }
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
