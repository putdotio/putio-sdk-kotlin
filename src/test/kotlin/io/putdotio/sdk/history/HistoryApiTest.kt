package io.putdotio.sdk.history

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class HistoryApiTest {
    @Test
    fun `list decodes history events`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "events": [
                    {
                      "id": 1,
                      "user_id": 42,
                      "type": "TRANSFER_COMPLETED",
                      "created_at": "2026-04-20T10:00:00Z",
                      "file_id": 99,
                      "file_name": "Movie.mkv"
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
                val events = sdk.history.list()
                assertEquals(1, events.size)
                assertEquals(HistoryEventType.TRANSFER_COMPLETED, events.first().type)
                assertEquals(99L, events.first().fileId)
            }
        }
    }

    @Test
    fun `clear posts to delete endpoint`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.history.clear()
            }
        }

        assertEquals("/v2/events/delete", server.takeRequest().target)
    }

    @Test
    fun `list preserves unknown history event types`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "events": [
                    {
                      "id": 7,
                      "user_id": 42,
                      "type": "FUTURE_EVENT",
                      "created_at": "2026-04-20T10:00:00Z"
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
                val event = sdk.history.list().first()
                assertEquals("FUTURE_EVENT", event.type.raw)
                assertFalse(event.type.isKnown)
            }
        }
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
