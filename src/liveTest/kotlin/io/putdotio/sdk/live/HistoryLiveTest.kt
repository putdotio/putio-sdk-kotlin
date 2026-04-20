package io.putdotio.sdk.live

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class HistoryLiveTest {
    @Test
    fun `history list decodes from the live API`() = runBlocking {
        LiveSupport.newAuthedClient().use { sdk ->
            val events = sdk.history.list()

            if (events.isNotEmpty()) {
                assertTrue(events.first().id > 0)
                assertTrue(events.first().createdAt.isNotBlank())
            }
        }
    }
}
