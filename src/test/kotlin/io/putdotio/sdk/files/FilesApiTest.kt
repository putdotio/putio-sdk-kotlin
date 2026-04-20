package io.putdotio.sdk.files

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class FilesApiTest {
    @Test
    fun `list applies typescript-style detail flags by default`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "parent": null,
                  "files": [],
                  "cursor": null
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
                sdk.files.list(parentId = 0)
            }
        }

        val request = server.takeRequest()
        assertEquals(
            "/v2/files/list?parent_id=0&mp4_status_parent=1&stream_url_parent=1&mp4_stream_url_parent=1&video_metadata_parent=1",
            request.target,
        )
    }

    @Test
    fun `createFolder sends form data`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "file": {
                    "id": 1,
                    "name": "Movies",
                    "size": 0,
                    "created_at": "2026-04-20T10:00:00Z",
                    "updated_at": "2026-04-20T10:00:00Z",
                    "file_type": "FOLDER",
                    "folder_type": "REGULAR"
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
                sdk.files.createFolder(name = "Movies", parentId = 0)
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/create-folder", request.target)
        assertEquals("name=Movies&parent_id=0", request.body!!.utf8())
    }

    @Test
    fun `list preserves unknown backend file types`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "parent": null,
                  "files": [
                    {
                      "id": 1,
                      "name": "Mystery",
                      "size": 0,
                      "created_at": "2026-04-20T10:00:00Z",
                      "updated_at": "2026-04-20T10:00:00Z",
                      "file_type": "BOOK",
                      "folder_type": "MAGIC_SHELF"
                    }
                  ],
                  "cursor": null
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
                val files = sdk.files.list(parentId = 0).files
                assertEquals("BOOK", files.first().fileType.raw)
                assertFalse(files.first().fileType.isKnown)
                assertEquals("MAGIC_SHELF", files.first().folderType.raw)
                assertFalse(files.first().folderType.isKnown)
            }
        }
    }

    @Test
    fun `delete returns typed result envelope`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "cursor": "next-page",
                  "skipped": 2
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
                val result = sdk.files.delete(fileIds = listOf(1, 2))
                assertEquals("next-page", result.cursor)
                assertEquals(2, result.skipped)
            }
        }
    }

    @Test
    fun `move returns per-file errors`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "errors": [
                    {
                      "error_type": "NOT_FOUND",
                      "id": 2,
                      "name": "Missing.mkv",
                      "status_code": 404
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
                val errors = sdk.files.move(fileIds = listOf(1, 2), parentId = 9)
                assertEquals(1, errors.size)
                assertEquals("NOT_FOUND", errors.first().errorType)
                assertEquals(2L, errors.first().id)
            }
        }
    }

    @Test
    fun `api failures become typed sdk errors`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body(
                    """
                    {
                      "status": "ERROR",
                      "status_code": 404,
                      "error_type": "NOT_FOUND",
                      "message": "file not found"
                    }
                    """.trimIndent(),
                )
                .build(),
        )

        val error = assertFailsWith<PutioApiException> {
            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.files.get(fileId = 99)
                }
            }
        }

        assertEquals(404, error.statusCode)
        assertEquals("NOT_FOUND", error.errorType)
    }

    @Test
    fun `buildDownloadUrl appends oauth token query`() {
        val sdk = PutioClient()

        assertEquals(
            "https://api.put.io/v2/files/10/download?oauth_token=abc",
            sdk.files.buildDownloadUrl(fileId = 10, accessToken = "abc"),
        )
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
