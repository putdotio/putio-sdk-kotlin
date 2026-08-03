package io.putdotio.sdk.files

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun `list encodes optional typed filters`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "parent": null,
                  "files": [],
                  "cursor": null,
                  "total": 0
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
                sdk.files.list(
                    parentId = 42,
                    query = FilesListQuery(
                        perPage = 25,
                        total = true,
                        hidden = true,
                        noCursor = true,
                        contentType = "video",
                        fileType = PutioFileType.VIDEO,
                        sortBy = "NAME_ASC",
                        mp4Status = true,
                    ),
                )
            }
        }

        val request = server.takeRequest()
        assertEquals(
            "/v2/files/list?parent_id=42&mp4_status_parent=1&stream_url_parent=1&mp4_stream_url_parent=1&video_metadata_parent=1&per_page=25&total=1&hidden=1&no_cursor=1&content_type=video&file_type=VIDEO&sort_by=NAME_ASC&mp4_status=1",
            request.target,
        )
    }

    @Test
    fun `continueList posts cursor and keeps pagination typed`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "parent": null,
                  "files": [],
                  "cursor": "next-page"
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
                val response = sdk.files.continueList(
                    cursor = "cursor-123",
                    query = FilesContinueQuery(perPage = 25),
                )
                assertEquals("next-page", response.cursor)
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/list/continue?per_page=25", request.target)
        assertEquals("cursor=cursor-123", request.body!!.utf8())
    }

    @Test
    fun `continueSearch posts cursor and keeps pagination typed`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "files": [],
                  "cursor": "next-search-page",
                  "total": 0
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
                val response = sdk.files.continueSearch(
                    cursor = "search-cursor-123",
                    query = FilesContinueQuery(perPage = 10),
                )
                assertEquals("next-search-page", response.cursor)
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/search/continue?per_page=10", request.target)
        assertEquals("cursor=search-cursor-123", request.body!!.utf8())
    }

    @Test
    fun `continue calls can omit pagination query`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK","files":[],"cursor":null}""").build())
        server.enqueue(MockResponse.Builder().body("""{"status":"OK","files":[],"cursor":null,"total":0}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.files.continueList(cursor = "list-cursor")
                sdk.files.continueSearch(cursor = "search-cursor")
            }
        }

        val listRequest = server.takeRequest()
        val searchRequest = server.takeRequest()
        assertEquals("/v2/files/list/continue", listRequest.target)
        assertEquals("cursor=list-cursor", listRequest.body!!.utf8())
        assertEquals("/v2/files/search/continue", searchRequest.target)
        assertEquals("cursor=search-cursor", searchRequest.body!!.utf8())
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
                val result = sdk.files.delete(fileIds = listOf(1, 2), skipTrash = true)
                assertEquals("next-page", result.cursor)
                assertEquals(2, result.skipped)
            }
        }

        val request = server.takeRequest()
        assertEquals(
            "/v2/files/delete?skip_nonexistents=true&skip_owner_check=false&skip_trash=true",
            request.target,
        )
        assertEquals("file_ids=1%2C2", request.body!!.utf8())
    }

    @Test
    fun `delete preserves the account trash setting by default`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """{"status":"OK","cursor":null,"skipped":0}""",
            ).build(),
        )

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.files.delete(fileIds = listOf(1))
            }
        }

        assertEquals(
            "/v2/files/delete?skip_nonexistents=true&skip_owner_check=false",
            server.takeRequest().target,
        )
    }

    @Test
    fun `copy posts file ids to disk copy endpoint`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.files.copy(fileIds = listOf(1, 2))
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/copy-to-disk", request.target)
        assertEquals("file_ids=1%2C2", request.body!!.utf8())
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
    fun `rename posts typed file name mutation`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.files.rename(fileId = 9, name = "Episode 2.mkv")
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/rename", request.target)
        assertEquals("file_id=9&name=Episode+2.mkv", request.body!!.utf8())
    }

    @Test
    fun `file sort endpoints post mutations`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.files.setSortBy(fileId = 9, sortBy = "DATE_DESC")
                sdk.files.resetFileSpecificSortSettings()
            }
        }

        val setRequest = server.takeRequest()
        val resetRequest = server.takeRequest()
        assertEquals("/v2/files/set-sort-by", setRequest.target)
        assertEquals("file_id=9&sort_by=DATE_DESC", setRequest.body!!.utf8())
        assertEquals("/v2/files/remove-sort-by-settings", resetRequest.target)
    }

    @Test
    fun `get sends file detail flags by default`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "file": {
                    "id": 9,
                    "name": "Movie.mkv",
                    "size": 123,
                    "created_at": "2026-04-20T10:00:00Z",
                    "updated_at": "2026-04-20T10:00:00Z",
                    "file_type": "VIDEO",
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
                sdk.files.get(fileId = 9)
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/9?mp4_size=1&start_from=1&stream_url=1&mp4_stream_url=1", request.target)
    }

    @Test
    fun `get can omit file detail flags`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "file": {
                    "id": 9,
                    "name": "Movie.mkv",
                    "size": 123,
                    "created_at": "2026-04-20T10:00:00Z",
                    "file_type": "VIDEO"
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
                sdk.files.get(
                    fileId = 9,
                    query = FileDetailsQuery(
                        mp4Size = false,
                        startFrom = false,
                        streamUrl = false,
                        mp4StreamUrl = false,
                    ),
                )
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/9", request.target)
    }

    @Test
    fun `getStartFrom decodes numeric offset`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "start_from": 93.5
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
                val startFrom = sdk.files.getStartFrom(fileId = 11)
                assertEquals(93.5, startFrom)
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/11/start-from", request.target)
    }

    @Test
    fun `listSubtitles sends languages filter and decodes payload`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "default": "en-key",
                  "subtitles": [
                    {
                      "key": "en-key",
                      "format": "vtt",
                      "language": "English",
                      "language_code": "en",
                      "name": "English",
                      "source": "opensubtitles",
                      "url": "https://example.com/subtitles/en.vtt"
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
                val response = sdk.files.listSubtitles(fileId = 7, languages = listOf("en", "tr"))
                assertEquals("en-key", response.defaultKey)
                assertEquals(1, response.subtitles.size)
                assertEquals("vtt", response.subtitles.first().format)
                assertEquals("en", response.subtitles.first().languageCode)
            }
        }

        val request = server.takeRequest()
        assertEquals("/v2/files/7/subtitles?languages=en%2Ctr", request.target)
    }

    @Test
    fun `api failures become operation-aware sdk errors`() = withServer { server ->
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

        val error = assertFailsWith<PutioOperationException> {
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

        assertEquals("files", error.domain)
        assertEquals("get", error.operation)
        val underlying = assertIs<PutioApiException>(error.underlyingError)
        assertEquals(404, underlying.statusCode)
        assertEquals("NOT_FOUND", underlying.errorType)
    }

    @Test
    fun `buildDownloadUrl appends oauth token query`() {
        val sdk = PutioClient()
        val videoFile = PutioFile(
            id = 11,
            name = "Video.mkv",
            createdAt = "2026-04-20T10:00:00Z",
            fileType = PutioFileType.VIDEO,
        )
        val audioNextFile = NextFile(
            id = 12,
            name = "Song.mp3",
            fileType = NextFileType.AUDIO,
        )
        val folderFile = PutioFile(
            id = 13,
            name = "Folder",
            createdAt = "2026-04-20T10:00:00Z",
            fileType = PutioFileType.FOLDER,
        )

        assertEquals(
            "https://api.put.io/v2/files/10/download?oauth_token=abc",
            sdk.files.buildDownloadUrl(fileId = 10, accessToken = "abc"),
        )
        assertEquals(
            "https://api.put.io/v2/files/10/mp4/download?oauth_token=abc",
            sdk.files.buildMp4DownloadUrl(fileId = 10, accessToken = "abc"),
        )
        assertEquals(
            "https://api.put.io/v2/files/10/stream?oauth_token=abc",
            sdk.files.buildAudioStreamUrl(fileId = 10, accessToken = "abc"),
        )
        assertEquals(
            "https://api.put.io/v2/files/10/hls/media.m3u8?oauth_token=abc&subtitle_key=all",
            sdk.files.buildHlsStreamUrl(fileId = 10, accessToken = "abc"),
        )
        assertEquals(
            "https://api.put.io/v2/files/11/hls/media.m3u8?oauth_token=abc&subtitle_key=all",
            sdk.files.buildStreamUrl(file = videoFile, accessToken = "abc"),
        )
        assertEquals(
            "https://api.put.io/v2/files/12/stream?oauth_token=abc",
            sdk.files.buildStreamUrl(nextFile = audioNextFile, accessToken = "abc"),
        )
        assertEquals(null, sdk.files.buildStreamUrl(file = folderFile, accessToken = "abc"))
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
