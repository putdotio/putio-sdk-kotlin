package io.putdotio.sdk.history

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
    fun `delete posts to scoped delete endpoint`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.history.delete(eventId = 17)
            }
        }

        assertEquals("/v2/events/delete/17", server.takeRequest().target)
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

    @Test
    fun `list wraps invalid pagination with events operation context`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body(
                    """
                    {
                      "status": "ERROR",
                      "status_code": 400,
                      "error_type": "INVALID_PER_PAGE",
                      "message": "per_page must be positive"
                    }
                    """.trimIndent(),
                )
                .build(),
        )

        val error = assertFailsWith<PutioOperationException> {
            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.history.list(HistoryListQuery(perPage = 0))
                }
            }
        }

        assertEquals("events", error.domain)
        assertEquals("list", error.operation)
        val underlying = assertIs<PutioApiException>(error.underlyingError)
        assertEquals("INVALID_PER_PAGE", underlying.errorType)
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
