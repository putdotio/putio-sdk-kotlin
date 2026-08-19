package io.putdotio.sdk.ifttt

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class IftttApiTest {
    @Test
    fun `sendPlaybackEvent posts nested event payload`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.ifttt.sendPlaybackEvent(
                        IftttPlaybackEventInput(
                            eventType = "video_started",
                            ingredients =
                                IftttPlaybackEventIngredients(
                                    fileId = 42,
                                    fileName = "Movie.mkv",
                                    fileType = "VIDEO",
                                ),
                        ),
                    )
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/ifttt-client/event", request.target)
            assertEquals(
                """{"event_type":"video_started","ingredients":{"file_id":42,"file_name":"Movie.mkv","file_type":"VIDEO"}}""",
                request.body!!.utf8(),
            )
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
