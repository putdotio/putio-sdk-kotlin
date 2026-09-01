package io.putdotio.sdk.live

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.account.AccountInfoQuery
import io.putdotio.sdk.files.FileDetailsQuery
import io.putdotio.sdk.files.FilesSearchQuery
import io.putdotio.sdk.files.PlaybackMediaCredential
import io.putdotio.sdk.files.PlaybackPreference
import io.putdotio.sdk.files.PlaybackRequest
import io.putdotio.sdk.files.PlaybackResolution
import io.putdotio.sdk.files.PlaybackSourceKind
import io.putdotio.sdk.files.PutioFile
import io.putdotio.sdk.files.PutioFileType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FilesPlaybackLiveTest {
    @Test
    fun `playback source resolves for an owned video candidate`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val candidate =
                    sdk.files.get(
                        fileId = LiveSupport.requirePlaybackFixtureId(),
                        query =
                            FileDetailsQuery(
                                mp4Status = true,
                                streamUrl = false,
                                mp4StreamUrl = false,
                            ),
                    )
                assertEquals(PutioFileType.VIDEO, candidate.fileType)
                assertEquals(false, candidate.isShared)
                assertEquals(true, candidate.isMp4Available)
                val account = sdk.account.getInfo(AccountInfoQuery(downloadToken = true))
                val downloadToken =
                    assertNotNull(
                        account.downloadToken,
                        "Dedicated live-test profile must return download_token",
                    )

                val source =
                    assertIs<PlaybackResolution.Ready>(
                        sdk.files.resolvePlayback(
                            PlaybackRequest(
                                fileId = candidate.id,
                                mediaCredential = PlaybackMediaCredential.downloadToken(downloadToken),
                                preference = PlaybackPreference.MP4,
                                useStartFrom = account.settings.useStartFrom,
                                includeSidecarSubtitles = false,
                            ),
                        ),
                    ).source
                assertEquals(candidate.id, source.fileId)
                assertEquals(PlaybackSourceKind.MP4, source.kind)
                assertTrue(source.startFromSeconds.isFinite())
                assertTrue(source.startFromSeconds >= 0.0)
                assertEquals("/v2/files/${candidate.id}/mp4/stream", source.url.encodedPath)
                assertEquals(setOf("oauth_token"), source.url.queryParameterNames)
                assertEquals("<redacted credential URL>", source.url.toString())
            }
        }
    }

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
