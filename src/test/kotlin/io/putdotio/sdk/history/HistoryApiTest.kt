package io.putdotio.sdk.history

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class HistoryApiTest {
    @Test
    fun `list decodes history events`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "has_more": true,
                          "events": [
                            {
                              "id": 1,
                              "user_id": 42,
                              "type": "transfer_completed",
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
                    val response = sdk.history.list(HistoryListQuery(perPage = 25, before = 100))
                    assertEquals(true, response.hasMore)
                    assertEquals(
                        listOf(
                            HistoryEvent(
                                id = 1,
                                userId = 42,
                                createdAt = "2026-04-20T10:00:00Z",
                                type = HistoryEventType.TRANSFER_COMPLETED,
                                fileId = 99,
                                fileName = "Movie.mkv",
                            ),
                        ),
                        response.events,
                    )
                }
            }

            val request = server.takeRequest()
            assertEquals("/v2/events/list?per_page=25&before=100", request.target)
        }

    @Test
    fun `list decodes the sharer and sizes the API sends per event type`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "has_more": false,
                          "events": [
                            {
                              "created_at": "2026-11-16T13:49:33",
                              "id": 311959680,
                              "user_id": 306077,
                              "file_id": 1623501834,
                              "file_name": "__playback-triage__",
                              "file_size": 1097879989492,
                              "sharing_user_name": "devs-admin",
                              "type": "file_shared"
                            },
                            {
                              "created_at": "2026-11-09T17:55:07",
                              "id": 311815611,
                              "user_id": 306077,
                              "source": "https://raw.githubusercontent.com/putdotio/putio-android/main/LICENSE",
                              "transfer_name": "LICENSE",
                              "file_id": 1622158611,
                              "transfer_size": 636,
                              "type": "transfer_completed"
                            },
                            {
                              "created_at": "2026-11-20T14:43:55",
                              "id": 312030872,
                              "user_id": 306077,
                              "file_id": 1624430777,
                              "file_name": "upload.txt",
                              "file_size": 24,
                              "type": "upload"
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
                    val (shared, transfer, upload) = sdk.history.list().events
                    assertEquals("devs-admin", shared.sharingUserName)
                    assertEquals(1_097_879_989_492L, shared.fileSize)
                    assertNull(shared.transferSize)
                    assertEquals(636L, transfer.transferSize)
                    assertNull(transfer.fileSize)
                    assertNull(transfer.sharingUserName)
                    assertEquals(24L, upload.fileSize)
                    assertNull(upload.sharingUserName)
                }
            }
        }

    @Test
    fun `clear posts to delete endpoint`() =
        withServer { server ->
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
    fun `delete posts to scoped delete endpoint`() =
        withServer { server ->
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
    fun `list preserves unknown history event types`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "has_more": false,
                          "events": [
                            {
                              "id": 7,
                              "user_id": 42,
                              "type": "FUTURE_EVENT",
                              "created_at": "2026-04-20T10:00:00Z",
                              "file_id": 99,
                              "file_name": "Movie.mkv",
                              "transfer_id": 55,
                              "transfer_name": "Transfer",
                              "rss_filter_title": "Weekly feed",
                              "zip_id": 4,
                              "icon": "video"
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
                    val response = sdk.history.list()
                    val event = response.events.first()
                    assertFalse(response.hasMore)
                    assertEquals("FUTURE_EVENT", event.type.raw)
                    assertFalse(event.type.isKnown)
                    assertEquals(99L, event.fileId)
                    assertEquals("Transfer", event.transferName)
                    assertEquals("Weekly feed", event.rssFilterTitle)
                    assertEquals(4L, event.zipId)
                    assertEquals("video", event.icon)
                }
            }
        }

    @Test
    fun `list wraps invalid pagination with events operation context`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
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
                    ).build(),
            )

            val error =
                assertFailsWith<PutioOperationException> {
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
