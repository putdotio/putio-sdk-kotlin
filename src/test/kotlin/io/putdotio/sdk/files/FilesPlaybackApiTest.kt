package io.putdotio.sdk.files

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class FilesPlaybackApiTest {
    @Test
    fun `search uses files search endpoint`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "cursor": null,
                  "total": 1,
                  "files": [
                    {
                      "id": 2,
                      "name": "Example",
                      "size": 10,
                      "created_at": "2026-04-20T10:00:00Z",
                      "updated_at": "2026-04-20T10:00:00Z",
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
                val response = sdk.files.search(
                    FilesSearchQuery(
                        keyword = "example",
                        perPage = 20,
                        type = listOf(PutioFileType.VIDEO, PutioFileType.AUDIO),
                    ),
                )
                assertEquals(1, response.total)
                assertEquals("Example", response.files.first().name)
            }
        }

        assertEquals("/v2/files/search?query=example&per_page=20&type=VIDEO%2CAUDIO", server.takeRequest().target)
    }

    @Test
    fun `listSubtitles decodes subtitle payload`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "default": "en",
                  "subtitles": [
                    {
                      "key": "en",
                      "language": "English",
                      "language_code": "en",
                      "name": "English",
                      "source": "opensubtitles",
                      "url": "https://example.com/sub.srt"
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
                val response = sdk.files.listSubtitles(5)
                assertEquals("en", response.defaultKey)
                assertEquals("https://example.com/sub.srt", response.subtitles.first().url)
            }
        }
    }

    @Test
    fun `findNextFile decodes playback-adjacent next media`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "next_file": {
                    "id": 42,
                    "name": "Next Episode.mkv",
                    "parent_id": 9,
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
                val nextFile = sdk.files.findNextFile(fileId = 41, fileType = NextFileType.VIDEO)
                assertEquals(42L, nextFile.id)
                assertEquals("Next Episode.mkv", nextFile.name)
                assertEquals(NextFileType.VIDEO, nextFile.fileType)
            }
        }

        assertEquals("/v2/files/41/next-file?file_type=VIDEO", server.takeRequest().target)
    }

    @Test
    fun `mp4 conversion endpoints decode forward-compatible status`() = withServer { server ->
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "mp4": {
                    "id": 10,
                    "percent_done": 25,
                    "status": "CONVERTING"
                  }
                }
                """.trimIndent(),
            ).build(),
        )
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "status": "OK",
                  "mp4": {
                    "id": 10,
                    "percent_done": 100,
                    "size": 2048,
                    "status": "COMPLETED"
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
                val started = sdk.files.startMp4Conversion(fileId = 10)
                val status = sdk.files.getMp4ConversionStatus(fileId = 10)

                assertEquals(FileMp4ConversionStatus.CONVERTING, started.status)
                assertEquals(25.0, started.percentDone)
                assertEquals(FileMp4ConversionStatus.COMPLETED, status.status)
                assertEquals(2048L, status.size)
            }
        }

        val startRequest = server.takeRequest()
        val statusRequest = server.takeRequest()
        assertEquals("/v2/files/10/mp4", startRequest.target)
        assertEquals("/v2/files/10/mp4", statusRequest.target)
    }

    @Test
    fun `setStartFrom and resetStartFrom hit playback endpoints`() = withServer { server ->
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "token",
                    baseUrl = server.url("/v2/").toString(),
                ),
            ).use { sdk ->
                sdk.files.setStartFrom(fileId = 10, time = 42.0)
                sdk.files.resetStartFrom(fileId = 10)
            }
        }

        val setRequest = server.takeRequest()
        val resetRequest = server.takeRequest()
        assertEquals("/v2/files/10/start-from/set", setRequest.target)
        assertEquals("time=42.0", setRequest.body!!.utf8())
        assertEquals("/v2/files/10/start-from/delete", resetRequest.target)
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
