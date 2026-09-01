package io.putdotio.sdk.live

import io.putdotio.sdk.errors.PutioOperationErrorReason
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.files.FilesContinueQuery
import io.putdotio.sdk.files.FilesListQuery
import io.putdotio.sdk.transfers.TransfersListQuery
import io.putdotio.sdk.trash.TrashContinueQuery
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CursorContinuationLiveTest {
    @Test
    fun `files cursors continue without sdk interpretation`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val createdIds = mutableListOf<Long>()
                try {
                    repeat(2) {
                        createdIds +=
                            sdk.files
                                .createFolder(
                                    name = LiveSupport.uniqueName("putio-kotlin-cursor"),
                                    parentId = 0,
                                ).id
                    }

                    val first = sdk.files.list(parentId = 0, query = FilesListQuery(perPage = 1))
                    val cursor = assertNotNull(first.cursor)

                    val continued = sdk.files.continueList(cursor, FilesContinueQuery(perPage = 1))

                    assertTrue(continued.files.size <= 1)
                    continued.cursor?.let { assertTrue(it.isNotBlank()) }
                } finally {
                    if (createdIds.isNotEmpty()) {
                        sdk.files.delete(fileIds = createdIds, skipTrash = true)
                    }
                }
            }
        }
    }

    @Test
    fun `invalid cursors retain continuation operation context`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                assertContinuationFailure("files", "continueList") {
                    sdk.files.continueList("invalid")
                }
                assertContinuationFailure("files", "continueSearch") {
                    sdk.files.continueSearch("invalid")
                }
                assertContinuationFailure("trash", "continueList") {
                    sdk.trash.continueList("invalid", TrashContinueQuery(perPage = 1))
                }
                assertContinuationFailure("transfers", "continueList") {
                    sdk.transfers.continueList("invalid", TransfersListQuery(perPage = 1))
                }
            }
        }
    }

    private suspend fun assertContinuationFailure(
        domain: String,
        operation: String,
        request: suspend () -> Unit,
    ) {
        val error = assertFailsWith<PutioOperationException> { request() }
        assertEquals(domain, error.domain)
        assertEquals(operation, error.operation)
        val reason = assertNotNull(error.reason as? PutioOperationErrorReason.StatusCode)
        assertEquals(400, reason.statusCode)
    }
}
