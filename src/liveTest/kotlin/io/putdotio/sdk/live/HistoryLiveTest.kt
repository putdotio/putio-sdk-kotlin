package io.putdotio.sdk.live

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class HistoryLiveTest {
    @Test
    fun `history list decodes from the live API`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val response = sdk.history.list()

                if (response.events.isNotEmpty()) {
                    assertTrue(response.events.first().id > 0)
                    assertTrue(response.events.first().createdAt.isNotBlank())
                }
            }
        }
    }
}
