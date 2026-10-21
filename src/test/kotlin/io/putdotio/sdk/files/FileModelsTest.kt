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
    fun `breadcrumbs decode id and name`() {
        val breadcrumb =
            json.decodeFromString(
                FileBreadcrumb.serializer(),
                """{"id":9,"name":"Movies"}""",
            )

        assertEquals(FileBreadcrumb(id = 9, name = "Movies"), breadcrumb)
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
    fun `file details query preserves positional constructor semantics`() {
        assertEquals(emptyMap(), FileDetailsQuery(false, false, false, false).toQueryMap())
        assertEquals(
            mapOf("mp4_status" to "1"),
            FileDetailsQuery(false, false, false, false, true).toQueryMap(),
        )
    }

    @Test
    fun `list search and subtitle envelopes keep optional fields nullable by default`() {
        val list =
            json.decodeFromString(
                FilesListResponse.serializer(),
                """{"status":"OK"}""",
            )
        val search =
            json.decodeFromString(
                FileSearchResponse.serializer(),
                """{"status":"OK","total":0}""",
            )
        val subtitles =
            json.decodeFromString(
                FileSubtitlesResponse.serializer(),
                """{"status":"OK"}""",
            )
        val metadata =
            json.decodeFromString(
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
