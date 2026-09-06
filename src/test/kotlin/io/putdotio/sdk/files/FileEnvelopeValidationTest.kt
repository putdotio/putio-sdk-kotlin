package io.putdotio.sdk.files

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.errors.PutioSerializationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FileEnvelopeValidationTest {
    @Test
    fun `get rejects non-OK envelopes even when the file matches the requested ID`() {
        listOf("ERROR", "anything", "ok", "").forEach { status ->
            withResponse("""{"status":"$status","file":$fileJson}""") { sdk ->
                assertInvalid("get") { sdk.files.get(9) }
            }
        }
    }

    @Test
    fun `createFolder rejects non-OK envelopes with a valid folder`() =
        withResponse("""{"status":"ERROR","file":$fileJson}""") { sdk ->
            assertInvalid("createFolder") { sdk.files.createFolder("Folder", 0) }
        }

    @Test
    fun `get keeps missing and null status invalid`() {
        listOf("", """"status":null,""").forEach { status ->
            withResponse("""{$status"file":$fileJson}""") { sdk ->
                assertInvalid("get") { sdk.files.get(9) }
            }
        }
    }

    @Test
    fun `get keeps missing and null file invalid`() {
        listOf("", """"file":null,""").forEach { file ->
            withResponse("""{$file"status":"OK"}""") { sdk ->
                assertInvalid("get") { sdk.files.get(9) }
            }
        }
    }

    @Test
    fun `get returns the matching file from an OK envelope`() =
        withResponse("""{"status":"OK","file":$fileJson}""") { sdk ->
            val file = sdk.files.get(9, FileDetailsQuery(false, false, false, false))
            assertEquals(9L, file.id)
            assertEquals("Folder", file.name)
            assertEquals(0L, file.parentId)
            assertEquals(PutioFileType.FOLDER, file.fileType)
        }

    private suspend fun assertInvalid(
        operation: String,
        block: suspend () -> Unit,
    ) {
        val error = assertFailsWith<PutioOperationException> { block() }
        assertEquals("files", error.domain)
        assertEquals(operation, error.operation)
        assertIs<PutioSerializationException>(error.underlyingError)
    }

    private fun withResponse(
        body: String,
        block: suspend (PutioClient) -> Unit,
    ) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body(body).build())
            runBlocking {
                PutioClient(
                    PutioConfig(accessToken = "token", baseUrl = server.url("/v2/").toString()),
                ).use { sdk -> block(sdk) }
            }
        }
    }

    private val fileJson = """{"id":9,"name":"Folder","parent_id":0,"file_type":"FOLDER","created_at":"2026-09-06"}"""
}
