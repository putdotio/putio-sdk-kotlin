package io.putdotio.sdk.history

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `history models decode full payloads and preserve unknown types`() {
        val event =
            json.decodeFromString(
                HistoryEvent.serializer(),
                """
                {
                  "id": 7,
                  "user_id": 42,
                  "type": "FUTURE_EVENT",
                  "created_at": "2026-04-20T10:00:00Z",
                  "file_id": 99,
                  "file_name": "Movie.mkv",
                  "transfer_id": 55,
                  "transfer_name": "Transfer",
                  "rss_filter_title": "Weekly feed",
                  "zip_id": 4,
                  "icon": "video"
                }
                """.trimIndent(),
            )

        assertEquals("FUTURE_EVENT", event.type.raw)
        assertFalse(event.type.isKnown)
        assertEquals(99L, event.fileId)
        assertEquals("Transfer", event.transferName)
        assertEquals("Weekly feed", event.rssFilterTitle)
        assertEquals(4L, event.zipId)
        assertEquals("video", event.icon)
    }

    @Test
    fun `history events decode the lowercase wire type as the known value`() {
        val event =
            json.decodeFromString(
                HistoryEvent.serializer(),
                """{"id":1,"user_id":2,"type":"transfer_completed","created_at":"2026-09-09T15:25:32","file_id":3}""",
            )

        assertEquals(HistoryEventType.TRANSFER_COMPLETED, event.type)
        assertTrue(event.type.isKnown)
    }

    @Test
    fun `history list response preserves explicit pagination state`() {
        val response =
            json.decodeFromString(
                HistoryListResponse.serializer(),
                """{"status":"OK","has_more":true,"events":[]}""",
            )

        assertEquals("OK", response.status)
        assertEquals(true, response.hasMore)
        assertEquals(emptyList(), response.events)
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
