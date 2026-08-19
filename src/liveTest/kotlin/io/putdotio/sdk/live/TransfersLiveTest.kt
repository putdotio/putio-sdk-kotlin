package io.putdotio.sdk.live

import io.putdotio.sdk.errors.PutioOperationErrorReason
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.transfers.TransfersListQuery
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransfersLiveTest {
    @Test
    fun `transfers list count and info decode from the live API`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val listed = sdk.transfers.list(TransfersListQuery(perPage = 5))
                val count = sdk.transfers.count()
                val info = sdk.transfers.info(listOf("https://example.invalid/kotlin-live-transfer.iso"))

                assertTrue(listed.transfers.size <= 5)
                assertTrue(count >= 0)
                assertTrue(info.diskAvailable >= 0)
                assertEquals(1, info.items.size)
                assertEquals("https://example.invalid/kotlin-live-transfer.iso", info.items.first().url)
                assertEquals("OK", info.status)

                listed.cursor?.let { cursor ->
                    val continued = sdk.transfers.continueList(cursor, TransfersListQuery(perPage = 5))
                    assertTrue(continued.transfers.size <= 5)
                }

                listed.transfers.firstOrNull()?.let { transfer ->
                    val fetched = sdk.transfers.get(transfer.id)
                    assertEquals(transfer.id, fetched.id)
                    assertTrue(fetched.name.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `transfers oversized pagination yields typed live error`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val error =
                    assertFailsWith<PutioOperationException> {
                        sdk.transfers.list(TransfersListQuery(perPage = 1001))
                    }

                assertEquals("transfers", error.domain)
                assertEquals("list", error.operation)

                val reason = assertNotNull(error.reason as? PutioOperationErrorReason.StatusCode)
                assertEquals(400, reason.statusCode)
            }
        }
    }
}
