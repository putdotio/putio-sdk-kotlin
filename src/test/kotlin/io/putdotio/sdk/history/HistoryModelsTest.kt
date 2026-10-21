package io.putdotio.sdk.history

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `history event types fold case without the host locale`() {
        // Kotlin's lowercase() is locale-invariant; a Turkish default would map I to dotless i otherwise.
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            assertEquals(HistoryEventType.FILE_SHARED, HistoryEventType.fromRaw("FILE_SHARED"))
            assertEquals(HistoryEventType.PRIVATE_TORRENT_PIN, HistoryEventType.fromRaw("PRIVATE_TORRENT_PIN"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `history helpers build expected queries and keep known values canonical`() {
        assertEquals(
            mapOf("per_page" to "25", "before" to "42"),
            HistoryListQuery(perPage = 25, before = 42).toQueryMap(),
        )

        val known = HistoryEventType.fromRaw("transfer_completed")
        assertTrue(known.isKnown)
        assertEquals(HistoryEventType.TRANSFER_COMPLETED, known)
        assertEquals(HistoryEventType.TRANSFER_COMPLETED, HistoryEventType.fromRaw("TRANSFER_COMPLETED"))
        assertEquals("transfer_completed", HistoryEventType.fromRaw("TRANSFER_COMPLETED").raw)
        assertFalse(HistoryEventType("TRANSFER_COMPLETED").isKnown)
    }
}
