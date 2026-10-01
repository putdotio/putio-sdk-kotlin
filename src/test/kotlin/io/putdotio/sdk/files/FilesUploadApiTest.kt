package io.putdotio.sdk.files

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationErrorReason
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.errors.PutioSerializationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FilesUploadApiTest {
    @Test
    fun `upload posts multipart to the upload host and decodes a started transfer`() =
        withServers { api, upload ->
            upload.enqueue(MockResponse.Builder().body(TRANSFER_UPLOAD_PAYLOAD).build())
            val content = byteArrayOf(0x64, 0x34, 0x3a, 0x69, 0x6e, 0x66, 0x6f, 0x00, 0xff.toByte())

            val result =
                runBlocking {
                    client(api, upload).use { sdk ->
                        sdk.files.upload(
                            FileUploadInput(
                                content = content,
                                fileName = "Harbor film.torrent",
                                parentId = 12,
                                requireTorrent = true,
                                mediaType = "application/x-bittorrent",
                            ),
                        )
                    }
                }

            val transfer = assertIs<FileUploadResult.Transfer>(result).transfer
            assertEquals(77, transfer.id)
            assertEquals(12, transfer.saveParentId)
            assertEquals(0, api.requestCount)
            val request = upload.takeRequest()
            assertEquals("/v2/files/upload", request.target)
            assertEquals("Token token", request.headers["Authorization"])
            assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data; boundary="))
            val body = request.body!!.toByteArray()
            val text = String(body, Charsets.ISO_8859_1)
            assertTrue(text.contains(formPart("filename", "Harbor film.torrent")))
            assertTrue(text.contains(formPart("parent_id", "12")))
            assertTrue(text.contains(formPart("torrent", "true")))
            assertTrue(text.contains("name=\"file\"; filename=\"Harbor film.torrent\""))
            assertTrue(text.contains("Content-Type: application/x-bittorrent"))
            assertTrue(body.containsSlice(content), "file part carries the exact bytes")
        }

    @Test
    fun `upload omits optional fields and decodes a saved file`() =
        withServers { api, upload ->
            upload.enqueue(MockResponse.Builder().body(FILE_UPLOAD_PAYLOAD).build())

            val result =
                runBlocking {
                    client(api, upload).use { sdk ->
                        sdk.files.upload(FileUploadInput(content = "notes".toByteArray(), fileName = "notes.txt"))
                    }
                }

            assertEquals("notes.txt", assertIs<FileUploadResult.File>(result).file.name)
            val text = upload.takeRequest().body!!.utf8()
            assertFalse(text.contains("name=\"parent_id\""))
            assertFalse(text.contains("name=\"torrent\""))
            assertTrue(text.contains("Content-Type: application/octet-stream"))
        }

    @Test
    fun `upload maps NotTorrent to a known operation error`() =
        withServers { api, upload ->
            upload.enqueue(
                MockResponse
                    .Builder()
                    .code(400)
                    .body(
                        """{"status":"ERROR","status_code":400,"error_type":"NotTorrent","message":"File is not a torrent file."}""",
                    ).build(),
            )

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        client(api, upload).use { sdk ->
                            sdk.files.upload(
                                FileUploadInput(content = byteArrayOf(1), fileName = "a.torrent", requireTorrent = true),
                            )
                        }
                    }
                }

            assertEquals("files", error.domain)
            assertEquals("upload", error.operation)
            assertEquals(PutioOperationErrorReason.ErrorType("NotTorrent"), error.reason)
            assertEquals("NotTorrent", assertIs<PutioApiException>(error.underlyingError).errorType)
        }

    @Test
    fun `upload rejects envelopes without exactly one result`() =
        withServers { api, upload ->
            upload.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        client(api, upload).use { sdk ->
                            sdk.files.upload(FileUploadInput(content = byteArrayOf(1), fileName = "a.torrent"))
                        }
                    }
                }

            assertIs<PutioSerializationException>(error.underlyingError)
        }

    @Test
    fun `upload input validates names, parents, torrent content and media type, and redacts content`() {
        assertFailsWith<IllegalArgumentException> { FileUploadInput(byteArrayOf(), fileName = " ") }
        assertFailsWith<IllegalArgumentException> { FileUploadInput(byteArrayOf(), fileName = "a", parentId = -1) }
        assertFailsWith<IllegalArgumentException> {
            FileUploadInput(byteArrayOf(), fileName = "a.torrent", requireTorrent = true)
        }
        assertFailsWith<IllegalArgumentException> { FileUploadInput(byteArrayOf(1), fileName = "a", mediaType = "not a type") }
        FileUploadInput(byteArrayOf(), fileName = "empty.txt")
        val input = FileUploadInput("secret-bytes".toByteArray(), fileName = "a.torrent")
        assertFalse(input.toString().contains("secret"))
        assertEquals("https://upload.put.io/v2/", PutioConfig().uploadBaseUrl)
    }

    private fun formPart(
        name: String,
        value: String,
    ): String = "Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"

    private fun ByteArray.containsSlice(slice: ByteArray): Boolean =
        (0..size - slice.size).any { start -> slice.indices.all { this[start + it] == slice[it] } }

    private fun withServers(block: (MockWebServer, MockWebServer) -> Unit) {
        MockWebServer().use { api ->
            MockWebServer().use { upload ->
                api.start()
                upload.start()
                block(api, upload)
            }
        }
    }

    private fun client(
        api: MockWebServer,
        upload: MockWebServer,
    ): PutioClient =
        PutioClient(
            PutioConfig(
                accessToken = "token",
                baseUrl = api.url("/v2/").toString(),
                uploadBaseUrl = upload.url("/v2/").toString(),
            ),
        )
}

private val TRANSFER_UPLOAD_PAYLOAD =
    """
    {
      "status": "OK",
      "transfer": {
        "id": 77,
        "name": "Harbor film",
        "type": "TORRENT",
        "status": "IN_QUEUE",
        "save_parent_id": 12,
        "created_at": "2026-04-20T10:00:00Z"
      }
    }
    """.trimIndent()

private val FILE_UPLOAD_PAYLOAD =
    """
    {
      "status": "OK",
      "file": {
        "id": 5,
        "name": "notes.txt",
        "size": 5,
        "created_at": "2026-04-20T10:00:00Z",
        "updated_at": "2026-04-20T10:00:00Z",
        "file_type": "TEXT"
      }
    }
    """.trimIndent()
