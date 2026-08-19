package io.putdotio.sdk.live

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.files.FilesSearchQuery
import io.putdotio.sdk.files.PutioFile
import io.putdotio.sdk.files.PutioFileType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FilesPlaybackLiveTest {
    @Test
    fun `files subtitles decode for an owned video candidate`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val video = findOwnedVideoCandidate(sdk)
                assumeTrue(video != null, "No owned video candidate found for subtitles live coverage")
                val candidate = assertNotNull(video)

                val subtitles = sdk.files.listSubtitles(candidate.id)

                assertEquals("OK", subtitles.status)
                subtitles.defaultKey?.let { assertTrue(it.isNotBlank()) }

                subtitles.subtitles.firstOrNull()?.let { subtitle ->
                    assertTrue(subtitle.key.isNotBlank())
                    assertTrue(subtitle.languageCode.isNotBlank())
                    assertTrue(subtitle.url.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `files start-from roundtrips and restores for an owned video candidate`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val video = findOwnedVideoCandidate(sdk)
                assumeTrue(video != null, "No owned video candidate found for start-from live coverage")
                val candidate = assertNotNull(video)
                val before = sdk.files.getStartFrom(candidate.id)
                val probe = if (before == 37.0) 0.0 else 37.0

                try {
                    sdk.files.setStartFrom(fileId = candidate.id, time = probe)
                    val updated = sdk.files.getStartFrom(candidate.id)
                    assertEquals(probe, updated)

                    sdk.files.resetStartFrom(candidate.id)
                    val reset = sdk.files.getStartFrom(candidate.id)
                    assertEquals(0.0, reset)
                } finally {
                    if (before == 0.0) {
                        sdk.files.resetStartFrom(candidate.id)
                    } else {
                        sdk.files.setStartFrom(fileId = candidate.id, time = before)
                    }
                }
            }
        }
    }

    private suspend fun findOwnedVideoCandidate(sdk: PutioClient): PutioFile? =
        sdk.files
            .search(
                FilesSearchQuery(
                    keyword = "mp4",
                    perPage = 10,
                ),
            ).files
            .firstOrNull { file ->
                file.fileType == PutioFileType.VIDEO && !file.isShared
            }
}
