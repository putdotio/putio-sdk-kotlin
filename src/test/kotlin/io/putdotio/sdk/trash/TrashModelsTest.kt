package io.putdotio.sdk.trash

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TrashModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `trash models decode full payloads and preserve unknown values`() {
        val response =
            json.decodeFromString(
                TrashListResponse.serializer(),
                """
                {
                  "status": "OK",
                  "cursor": "next-page",
                  "total": 1,
                  "trash_size": 123,
                  "files": [
                    {
                      "id": 10,
                      "name": "Old Movie",
                      "icon": "video",
                      "parent_id": 2,
                      "size": 99,
                      "created_at": "2026-04-20T10:00:00Z",
                      "deleted_at": "2026-04-21T10:00:00Z",
                      "expiration_date": "2026-05-01T10:00:00Z",
                      "file_type": "BOOK",
                      "folder_type": "MAGIC_SHELF",
                      "video_metadata": {
                        "height": 1080,
                        "width": 1920,
                        "codec": "h264",
                        "duration": 61.5,
                        "aspect_ratio": 1.78
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        val file = response.files.first()
        assertEquals("next-page", response.cursor)
        assertEquals(1, response.total)
        assertEquals(123L, response.trashSize)
        assertEquals(2L, file.parentId)
        assertEquals("BOOK", file.fileType.raw)
        assertFalse(file.fileType.isKnown)
        assertEquals("MAGIC_SHELF", file.folderType.raw)
        assertFalse(file.folderType.isKnown)
        assertEquals(1080, file.videoMetadata?.height)
        assertEquals("2026-05-01T10:00:00Z", file.expirationDate)
    }

    @Test
    fun `trash query and bulk input helpers build expected maps`() {
        assertEquals(mapOf("per_page" to "25"), TrashListQuery(perPage = 25).toQueryMap())
        assertEquals(mapOf("file_ids" to "1,2"), TrashBulkInput(ids = listOf(1, 2)).toFormMap())
        assertEquals(mapOf("cursor" to "cursor-1"), TrashBulkInput(cursor = "cursor-1").toFormMap())
    }

    @Test
    fun `trash bulk input rejects empty selection`() {
        assertFailsWith<IllegalArgumentException> {
            TrashBulkInput()
        }
    }

    @Test
    fun `trash models keep nullable fields optional and default folder type`() {
        val response =
            json.decodeFromString(
                TrashListResponse.serializer(),
                """
                {
                  "status": "OK",
                  "files": [
                    {
                      "id": 22,
                      "name": "Episode",
                      "created_at": "2026-04-20T10:00:00Z",
                      "deleted_at": "2026-04-21T10:00:00Z",
                      "expiration_date": "2026-05-01T10:00:00Z",
                      "file_type": "VIDEO"
                    }
                  ]
                }
                """.trimIndent(),
            )

        val file = response.files.first()
        assertEquals(null, response.cursor)
        assertEquals(null, response.total)
        assertEquals(0L, response.trashSize)
        assertEquals(null, file.icon)
        assertEquals(null, file.parentId)
        assertEquals(0L, file.size)
        assertEquals("REGULAR", file.folderType.raw)
        assertEquals(null, file.videoMetadata)
    }
}
