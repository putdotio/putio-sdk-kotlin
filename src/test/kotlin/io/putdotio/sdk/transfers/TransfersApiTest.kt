package io.putdotio.sdk.transfers

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class TransfersApiTest {
    @Test
    fun `transfers endpoints build requests and decode typed responses`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body(transfersListPayload(cursor = "next-transfers", total = 3)).build())
            server.enqueue(MockResponse.Builder().body(transfersListPayload(cursor = null, total = null)).build())
            server.enqueue(MockResponse.Builder().body(transferEnvelopePayload(id = 42, status = "DOWNLOADING")).build())
            server.enqueue(MockResponse.Builder().body("""{"count":7,"status":"OK"}""").build())
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "disk_avail": 1024,
                          "ret": [
                            {
                              "url": "https://example.com/a",
                              "name": "a.iso",
                              "type_name": "URL",
                              "file_size": 100,
                              "human_size": "100 B"
                            }
                          ],
                          "status": "OK"
                        }
                        """.trimIndent(),
                    ).build(),
            )
            server.enqueue(MockResponse.Builder().body(transferEnvelopePayload(id = 43, status = "WAITING")).build())
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "errors": [
                            {
                              "error_type": "EMPTY_URL",
                              "status_code": 400,
                              "url": ""
                            }
                          ],
                          "transfers": [
                            ${transferPayload(id = 44, status = "IN_QUEUE")}
                          ],
                          "status": "OK"
                        }
                        """.trimIndent(),
                    ).build(),
            )
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
            server.enqueue(MockResponse.Builder().body("""{"deleted_ids":[42],"status":"OK"}""").build())
            server.enqueue(MockResponse.Builder().body("""{"deleted_ids":[],"status":"OK"}""").build())
            server.enqueue(MockResponse.Builder().body(transferEnvelopePayload(id = 42, status = "WAITING")).build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val listed = sdk.transfers.list(TransfersListQuery(perPage = 2))
                    val continued = sdk.transfers.continueList("next-transfers", TransfersListQuery(perPage = 1))
                    val fetched = sdk.transfers.get(42)
                    val count = sdk.transfers.count()
                    val info = sdk.transfers.info(listOf("https://example.com/a", "https://example.com/b"))
                    val added =
                        sdk.transfers.add(
                            TransferAddInput(
                                url = "https://example.com/file.torrent",
                                saveParentId = 9,
                                callbackUrl = "https://example.com/callback",
                            ),
                        )
                    val addedMany =
                        sdk.transfers.addMany(
                            listOf(
                                TransferAddInput(url = "https://example.com/one"),
                                TransferAddInput(url = "https://example.com/two", saveParentId = 9),
                            ),
                        )
                    val cancelled = sdk.transfers.cancel(listOf(42, 43))
                    val cleaned = sdk.transfers.clean(listOf(42))
                    val cleanedAll = sdk.transfers.clean()
                    val retried = sdk.transfers.retry(42)

                    assertEquals("next-transfers", listed.cursor)
                    assertEquals(3, listed.total)
                    assertEquals(TransferStatus.DOWNLOADING, listed.transfers.first().status)
                    assertNull(continued.cursor)
                    assertEquals(42L, fetched.id)
                    assertEquals("Ubuntu.iso", fetched.name)
                    assertEquals("magnet:?xt=urn:btih:example", fetched.source)
                    assertEquals(TransferType.TORRENT, fetched.type)
                    assertEquals(0L, fetched.saveParentId)
                    assertEquals(99L, fetched.fileId)
                    assertEquals(100L, fetched.downloadId)
                    assertEquals(1000.0, fetched.size)
                    assertEquals(50.0, fetched.percentDone)
                    assertEquals(50.0, fetched.completionPercent)
                    assertEquals(500.0, fetched.downloaded)
                    assertEquals(25.0, fetched.uploaded)
                    assertEquals(10.0, fetched.downSpeed)
                    assertEquals(1.0, fetched.upSpeed)
                    assertEquals(60.0, fetched.estimatedTime)
                    assertEquals(1.0, fetched.availability)
                    assertNull(fetched.errorMessage)
                    assertEquals("2026-04-20T10:00:00Z", fetched.createdAt)
                    assertNull(fetched.startedAt)
                    assertNull(fetched.finishedAt)
                    assertNull(fetched.callbackUrl)
                    assertEquals(0.5, fetched.currentRatio)
                    assertEquals(0.0, fetched.secondsSeeding)
                    assertEquals(true, fetched.isPrivate)
                    assertEquals("torrent", fetched.links.first().label)
                    assertEquals("https://api.put.io/v2/transfers/42/torrent", fetched.links.first().url)
                    assertEquals(true, fetched.userFileExists)
                    assertEquals(7, count)
                    assertEquals(1024.0, info.diskAvailable)
                    assertEquals("https://example.com/a", info.items.first().url)
                    assertEquals("a.iso", info.items.first().name)
                    assertEquals("URL", info.items.first().typeName)
                    assertEquals(100.0, info.items.first().fileSize)
                    assertEquals("100 B", info.items.first().humanSize)
                    assertNull(info.items.first().error)
                    assertNull(info.items.first().errorMessage)
                    assertEquals("OK", info.status)
                    assertEquals(43L, added.id)
                    assertEquals("EMPTY_URL", addedMany.errors.first().errorType)
                    assertEquals(400, addedMany.errors.first().statusCode)
                    assertEquals("", addedMany.errors.first().url)
                    assertEquals("OK", addedMany.status)
                    assertEquals(TransferStatus.IN_QUEUE, addedMany.transfers.first().status)
                    assertEquals("OK", cancelled.status)
                    assertEquals(listOf(42L), cleaned.deletedIds)
                    assertEquals("OK", cleaned.status)
                    assertEquals(emptyList(), cleanedAll.deletedIds)
                    assertEquals(TransferStatus.WAITING, retried.status)
                }
            }

            assertEquals("/v2/transfers/list?per_page=2", server.takeRequest().target)

            val continueRequest = server.takeRequest()
            assertEquals("/v2/transfers/list/continue?per_page=1", continueRequest.target)
            assertEquals("next-transfers", formValues(continueRequest.body!!.utf8())["cursor"])

            assertEquals("/v2/transfers/42", server.takeRequest().target)
            assertEquals("/v2/transfers/count", server.takeRequest().target)

            val infoRequest = server.takeRequest()
            assertEquals("/v2/transfers/info", infoRequest.target)
            assertEquals("https://example.com/a\nhttps://example.com/b", formValues(infoRequest.body!!.utf8())["urls"])

            val addRequest = server.takeRequest()
            assertEquals("/v2/transfers/add", addRequest.target)
            assertEquals(
                mapOf(
                    "url" to "https://example.com/file.torrent",
                    "save_parent_id" to "9",
                    "callback_url" to "https://example.com/callback",
                ),
                formValues(addRequest.body!!.utf8()),
            )

            val addManyRequest = server.takeRequest()
            assertEquals("/v2/transfers/add-multi", addManyRequest.target)
            assertEquals(
                """[{"url":"https://example.com/one"},{"url":"https://example.com/two","save_parent_id":9}]""",
                formValues(addManyRequest.body!!.utf8())["urls"],
            )

            val cancelRequest = server.takeRequest()
            assertEquals("/v2/transfers/cancel", cancelRequest.target)
            assertEquals("42,43", formValues(cancelRequest.body!!.utf8())["transfer_ids"])

            val cleanRequest = server.takeRequest()
            assertEquals("/v2/transfers/clean", cleanRequest.target)
            assertEquals("42", formValues(cleanRequest.body!!.utf8())["transfer_ids"])

            val cleanAllRequest = server.takeRequest()
            assertEquals("/v2/transfers/clean", cleanAllRequest.target)
            assertEquals(emptyMap(), formValues(cleanAllRequest.body!!.utf8()))

            val retryRequest = server.takeRequest()
            assertEquals("/v2/transfers/retry", retryRequest.target)
            assertEquals("42", formValues(retryRequest.body!!.utf8())["id"])
        }

    @Test
    fun `default list queries omit pagination`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body(transfersListPayload(cursor = "cursor", total = null)).build())
            server.enqueue(MockResponse.Builder().body(transfersListPayload(cursor = null, total = null)).build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.transfers.list()
                    sdk.transfers.continueList("cursor")
                }
            }

            assertEquals("/v2/transfers/list", server.takeRequest().target)
            val continueRequest = server.takeRequest()
            assertEquals("/v2/transfers/list/continue", continueRequest.target)
            assertEquals("cursor", formValues(continueRequest.body!!.utf8())["cursor"])
        }

    @Test
    fun `transfer value types and request helpers preserve raw values`() {
        assertEquals(true, TransferType.URL.isKnown)
        assertEquals(true, TransferType.PLAYLIST.isKnown)
        assertEquals(true, TransferType.LIVE_STREAM.isKnown)
        assertEquals(true, TransferType.NOT_AVAILABLE.isKnown)
        assertEquals("URL", TransferType.URL.toString())
        assertEquals("TORRENT", TransferType.fromRaw("TORRENT").raw)
        assertEquals("NEW_TYPE", TransferType.fromRaw("NEW_TYPE").raw)

        assertEquals(true, TransferStatus.WAITING.isKnown)
        assertEquals(true, TransferStatus.PREPARING_DOWNLOAD.isKnown)
        assertEquals(true, TransferStatus.WAITING_FOR_COMPLETE_QUEUE.isKnown)
        assertEquals(true, TransferStatus.WAITING_FOR_DOWNLOADER.isKnown)
        assertEquals(true, TransferStatus.COMPLETING.isKnown)
        assertEquals(true, TransferStatus.STOPPING.isKnown)
        assertEquals(true, TransferStatus.SEEDING.isKnown)
        assertEquals(true, TransferStatus.COMPLETED.isKnown)
        assertEquals(true, TransferStatus.ERROR.isKnown)
        assertEquals(true, TransferStatus.PREPARING_SEED.isKnown)
        assertEquals("DOWNLOADING", TransferStatus.DOWNLOADING.toString())
        assertEquals("IN_QUEUE", TransferStatus.fromRaw("IN_QUEUE").raw)
        assertEquals("NEW_STATUS", TransferStatus.fromRaw("NEW_STATUS").raw)

        assertEquals(mapOf("per_page" to "50"), TransfersListQuery(perPage = 50).toQueryMap())
        assertEquals(emptyMap(), TransfersListQuery().toQueryMap())
        assertEquals(
            mapOf(
                "url" to "https://example.com/a",
                "save_parent_id" to "9",
                "callback_url" to "https://example.com/callback",
            ),
            TransferAddInput(
                url = "https://example.com/a",
                saveParentId = 9,
                callbackUrl = "https://example.com/callback",
            ).toFormMap(),
        )
    }

    @Test
    fun `transfer models preserve unknown backend values`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body(transferEnvelopePayload(id = 50, type = "NEW_TYPE", status = "NEW_STATUS")).build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val transfer = sdk.transfers.get(50)
                    assertFalse(transfer.type.isKnown)
                    assertEquals("NEW_TYPE", transfer.type.raw)
                    assertFalse(transfer.status.isKnown)
                    assertEquals("NEW_STATUS", transfer.status.raw)
                }
            }
        }

    @Test
    fun `transfer api failures include operation context`() =
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
                          "error_type": "EMPTY_URL",
                          "message": "empty transfer URL"
                        }
                        """.trimIndent(),
                    ).build(),
            )

            val error =
                kotlin.test.assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                accessToken = "token",
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.transfers.add(TransferAddInput(url = ""))
                        }
                    }
                }

            assertEquals("transfers", error.domain)
            assertEquals("add", error.operation)
            val underlying = assertIs<PutioApiException>(error.underlyingError)
            assertEquals("EMPTY_URL", underlying.errorType)
        }

    private fun formValues(body: String): Map<String, String> {
        if (body.isBlank()) {
            return emptyMap()
        }

        return body.split("&").associate { pair ->
            val keyValue = pair.split("=", limit = 2)
            val key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8)
            val value = URLDecoder.decode(keyValue.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            key to value
        }
    }

    private fun transfersListPayload(
        cursor: String?,
        total: Int?,
    ): String {
        val cursorValue = cursor?.let { "\"$it\"" } ?: "null"
        val totalLine = total?.let { ",\"total\":$it" } ?: ""
        return """
            {
              "cursor": $cursorValue,
              "transfers": [
                ${transferPayload(id = 42, status = "DOWNLOADING")}
              ]$totalLine,
              "status": "OK"
            }
            """.trimIndent()
    }

    private fun transferEnvelopePayload(
        id: Long,
        type: String = "TORRENT",
        status: String,
    ): String =
        """
        {
          "transfer": ${transferPayload(id = id, type = type, status = status)},
          "status": "OK"
        }
        """.trimIndent()

    private fun transferPayload(
        id: Long,
        type: String = "TORRENT",
        status: String,
    ): String =
        """
        {
          "id": $id,
          "name": "Ubuntu.iso",
          "source": "magnet:?xt=urn:btih:example",
          "type": "$type",
          "status": "$status",
          "save_parent_id": 0,
          "file_id": 99,
          "download_id": 100,
          "size": 1000,
          "percent_done": 50,
          "completion_percent": 50,
          "downloaded": 500,
          "uploaded": 25,
          "down_speed": 10,
          "up_speed": 1,
          "estimated_time": 60,
          "availability": 1,
          "error_message": null,
          "created_at": "2026-04-20T10:00:00Z",
          "started_at": null,
          "finished_at": null,
          "callback_url": null,
          "current_ratio": 0.5,
          "seconds_seeding": 0,
          "is_private": true,
          "links": [
            {
              "label": "torrent",
              "url": "https://api.put.io/v2/transfers/$id/torrent"
            }
          ],
          "userfile_exists": true
        }
        """.trimIndent()

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
