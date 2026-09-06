package io.putdotio.sdk.trash

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.errors.PutioSerializationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class TrashResponseValidationTest {
    @Test
    fun `restore rejects non-OK acknowledgements on successful HTTP responses`() {
        listOf("ERROR", "anything", "ok", "").forEach { status ->
            assertRejected("""{"status":"$status"}""", "restore") {
                it.trash.restore(TrashBulkInput(ids = listOf(1)))
            }
        }
    }

    @Test
    fun `restore rejects missing and null acknowledgement status`() {
        listOf("{}", """{"status":null}""").forEach { body ->
            assertRejected(body, "restore") { it.trash.restore(TrashBulkInput(ids = listOf(1))) }
        }
    }

    @Test
    fun `restore accepts a bare acknowledgement`() =
        withResponse("""{"status":"OK"}""") { sdk ->
            assertEquals(OkResponse("OK"), sdk.trash.restore(TrashBulkInput(ids = listOf(1))))
        }

    @Test
    fun `list rejects missing and null files instead of reporting empty trash`() {
        listOf("", """"files":null,""").forEach { files ->
            assertRejected("""{$files"status":"OK","total":0,"trash_size":0}""", "list") {
                it.trash.list()
            }
        }
    }

    @Test
    fun `continuation rejects missing and null files instead of reporting empty trash`() {
        listOf("", """"files":null,""").forEach { files ->
            assertRejected("""{$files"status":"OK"}""", "continueList") {
                it.trash.continueList("cursor")
            }
        }
    }

    @Test
    fun `list rejects non-OK missing and null status`() {
        listOf(""""status":"ERROR",""", "", """"status":null,""").forEach { status ->
            assertRejected("""{$status"files":[],"total":0,"trash_size":0}""", "list") {
                it.trash.list()
            }
        }
    }

    @Test
    fun `continuation rejects non-OK missing and null status`() {
        listOf(""""status":"ERROR",""", "", """"status":null,""").forEach { status ->
            assertRejected("""{$status"files":[]}""", "continueList") {
                it.trash.continueList("cursor")
            }
        }
    }

    @Test
    fun `list requires an explicit nonnegative total`() {
        listOf("", """"total":null,""", """"total":-1,""").forEach { total ->
            assertRejected("""{$total"status":"OK","files":[],"trash_size":0}""", "list") {
                it.trash.list()
            }
        }
    }

    @Test
    fun `list requires an explicit nonnegative trash size`() {
        listOf("", """"trash_size":null,""", """"trash_size":-1,""").forEach { trashSize ->
            assertRejected("""{$trashSize"status":"OK","files":[],"total":0}""", "list") {
                it.trash.list()
            }
        }
    }

    @Test
    fun `list accepts empty trash with explicit zero aggregates`() =
        withResponse("""{"status":"OK","files":[],"total":0,"trash_size":0}""") { sdk ->
            val response = sdk.trash.list(TrashListQuery(perPage = 50))
            assertEquals(emptyList(), response.files)
            assertEquals(0, response.total)
            assertEquals(0L, response.trashSize)
            assertNull(response.cursor)
        }

    @Test
    fun `continuation accepts an empty page with another cursor and no aggregates`() =
        withResponse("""{"status":"OK","files":[],"cursor":"next"}""") { sdk ->
            val response = sdk.trash.continueList("cursor")
            assertEquals(emptyList(), response.files)
            assertEquals("next", response.cursor)
            assertNull(response.total)
            assertEquals(0L, response.trashSize)
        }

    @Test
    fun `continuation preserves optional aggregates when supplied`() =
        withResponse("""{"status":"OK","files":[],"total":3,"trash_size":123}""") { sdk ->
            val response = sdk.trash.continueList("cursor")
            assertEquals(3, response.total)
            assertEquals(123L, response.trashSize)
        }

    @Test
    fun `continuation rejects negative aggregates when supplied`() {
        listOf(""""total":-1""", """"trash_size":-1""").forEach { aggregate ->
            assertRejected("""{"status":"OK","files":[],$aggregate}""", "continueList") {
                it.trash.continueList("cursor")
            }
        }
    }

    @Test
    fun `restore preserves typed incomplete trash rejection`() =
        withResponse(
            """{"status":"ERROR","error_type":"TRASH_INCOMPLETE_TRASH","message":"Not finished"}""",
            statusCode = 400,
        ) { sdk ->
            val error =
                assertFailsWith<PutioOperationException> {
                    sdk.trash.restore(TrashBulkInput(ids = listOf(1)))
                }
            assertEquals("trash", error.domain)
            assertEquals("restore", error.operation)
            val underlying = assertIs<PutioApiException>(error.underlyingError)
            assertEquals(400, underlying.statusCode)
            assertEquals("TRASH_INCOMPLETE_TRASH", underlying.errorType)
        }

    private fun assertRejected(
        body: String,
        operation: String,
        call: suspend (PutioClient) -> Unit,
    ) = withResponse(body) { sdk ->
        val error = assertFailsWith<PutioOperationException> { call(sdk) }
        assertEquals("trash", error.domain)
        assertEquals(operation, error.operation)
        assertIs<PutioSerializationException>(error.underlyingError)
    }

    private fun withResponse(
        body: String,
        statusCode: Int = 200,
        block: suspend (PutioClient) -> Unit,
    ) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(statusCode)
                    .body(body)
                    .build(),
            )
            runBlocking {
                PutioClient(
                    PutioConfig(accessToken = "token", baseUrl = server.url("/v2/").toString()),
                ).use { sdk -> block(sdk) }
            }
        }
    }
}
