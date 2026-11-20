package io.putdotio.sdk.live

import io.putdotio.sdk.history.HistoryEventType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HistoryLiveTest {
    @Test
    fun `history list decodes from the live API`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val response = sdk.history.list()

                response.events
                    .filter { it.type == HistoryEventType.UPLOAD || it.type == HistoryEventType.FILE_SHARED }
                    .forEach { assertNotNull(it.fileSize) }
                response.events
                    .filter { it.type == HistoryEventType.FILE_SHARED }
                    .forEach { assertNotNull(it.sharingUserName) }
                response.events
                    .filter { it.type == HistoryEventType.TRANSFER_COMPLETED }
                    .forEach { assertNotNull(it.transferSize) }

                if (response.events.isNotEmpty()) {
                    assertTrue(response.events.first().id > 0)
                    assertTrue(
                        response.events
                            .first()
                            .createdAt
                            .isNotBlank(),
                    )
                }
            }
        }
    }
}
