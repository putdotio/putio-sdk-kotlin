package io.putdotio.sdk.trash

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class TrashApiTest {
    @Test
    fun `list decodes trash response`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "cursor": "next-page",
                  "trash_size": 123,
                  "files": [
                    {
                      "id": 10,
                      "name": "Old Movie",
                      "size": 99,
                      "created_at": "2026-04-20T10:00:00Z",
                      "deleted_at": "2026-04-21T10:00:00Z",
                      "expiration_date": "2026-05-01T10:00:00Z",
                      "file_type": "VIDEO",
                      "folder_type": "REGULAR"
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
                val response = sdk.trash.list()
                assertEquals("next-page", response.cursor)
                assertEquals(123L, response.trashSize)
                assertEquals(1, response.files.size)
            }
        }
    }

    @Test
    fun `restore can use ids`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.trash.restore(TrashBulkInput(ids = listOf(1, 2)))
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/trash/restore", request.target)
        assertEquals("file_ids=1%2C2", request.body!!.utf8())
    }

    @Test
    fun `restore wraps missing trash items with operation context`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body(
                    """
                    {
                      "status": "ERROR",
                      "status_code": 404,
                      "error_type": "NOT_FOUND",
                      "message": "trash item not found"
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
                    sdk.trash.restore(TrashBulkInput(ids = listOf(1)))
                }
            }
        }

        assertEquals("trash", error.domain)
        assertEquals("restore", error.operation)
        val underlying = assertIs<PutioApiException>(error.underlyingError)
        assertEquals(404, underlying.statusCode)
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
