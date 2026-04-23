package io.putdotio.sdk.files

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializers preserve file metadata and unknown values`() {
        val file = json.decodeFromString(
            PutioFile.serializer(),
            """
            {
              "id": 1,
              "name": "Mystery.mkv",
              "parent_id": 0,
              "size": 42,
              "created_at": "2026-04-20T10:00:00Z",
              "updated_at": "2026-04-20T11:00:00Z",
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
            """.trimIndent(),
        )

        assertEquals("BOOK", file.fileType.raw)
        assertFalse(file.fileType.isKnown)
        assertEquals("MAGIC_SHELF", file.folderType.raw)
        assertFalse(file.folderType.isKnown)
        assertEquals(1080, file.videoMetadata?.height)
        assertEquals(1.78, file.videoMetadata?.aspectRatio)
    }

    @Test
    fun `serializers decode breadcrumbs subtitles and start-from envelopes`() {
        val breadcrumb = json.decodeFromString(
            FileBreadcrumb.serializer(),
            """{"id":9,"name":"Movies"}""",
        )
        val subtitle = json.decodeFromString(
            FileSubtitle.serializer(),
            """
            {
              "key": "en-key",
              "format": "vtt",
              "language": "English",
              "language_code": "en",
              "name": "English",
              "source": "opensubtitles",
              "url": "https://example.com/subtitles/en.vtt"
            }
            """.trimIndent(),
        )
        val startFrom = json.decodeFromString(
            FileStartFromResponse.serializer(),
            """{"status":"OK","start_from":15.25}""",
        )

        assertEquals(9L, breadcrumb.id)
        assertEquals("Movies", breadcrumb.name)
        assertEquals("vtt", subtitle.format)
        assertEquals("en", subtitle.languageCode)
        assertEquals("https://example.com/subtitles/en.vtt", subtitle.url)
        assertEquals(15.25, startFrom.startFrom)
        assertEquals("OK", startFrom.status)
    }

    @Test
    fun `known file type helpers still round-trip to canonical values`() {
        val type = PutioFileType.fromRaw("VIDEO")
        val folderType = PutioFolderType.fromRaw("REGULAR")

        assertTrue(type.isKnown)
        assertEquals(PutioFileType.VIDEO, type)
        assertTrue(folderType.isKnown)
        assertEquals(PutioFolderType.REGULAR, folderType)
    }

    @Test
    fun `list search and subtitle envelopes keep optional fields nullable by default`() {
        val list = json.decodeFromString(
            FilesListResponse.serializer(),
            """{"status":"OK"}""",
        )
        val search = json.decodeFromString(
            FileSearchResponse.serializer(),
            """{"status":"OK","total":0}""",
        )
        val subtitles = json.decodeFromString(
            FileSubtitlesResponse.serializer(),
            """{"status":"OK"}""",
        )
        val metadata = json.decodeFromString(
            PutioVideoMetadata.serializer(),
            """{}""",
        )

        assertEquals(null, list.parent)
        assertTrue(list.files.isEmpty())
        assertEquals(null, list.cursor)
        assertEquals(null, list.total)
        assertTrue(search.files.isEmpty())
        assertEquals(0, search.total)
        assertEquals(null, subtitles.defaultKey)
        assertTrue(subtitles.subtitles.isEmpty())
        assertEquals(null, metadata.height)
        assertEquals(null, metadata.aspectRatio)
    }

    @Test
    fun `search envelopes require backend total instead of defaulting it`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                FileSearchResponse.serializer(),
                """{"status":"OK","files":[],"cursor":null}""",
            )
        }
    }
}
