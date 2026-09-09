package io.putdotio.sdk.files

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.errors.PutioSerializationException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FilesPlaybackApiTest {
    @Test
    fun `resolvePlayback returns HLS with embedded subtitles for ready video`() =
        withServer { server ->
            server.enqueue(playbackFileResponse(needConvert = false, startFrom = 42.5))

            val resolution =
                server.resolvePlayback(
                    PlaybackRequest(
                        fileId = 42,
                        mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                        preference = PlaybackPreference.HLS,
                        useStartFrom = true,
                        subtitleLanguages = listOf("en", "tr"),
                    ),
                )
            val source = assertIs<PlaybackResolution.Ready>(resolution).source

            assertEquals(PlaybackSourceKind.HLS, source.kind)
            assertEquals(42.5, source.startFromSeconds)
            assertEquals(PlaybackSubtitles.Embedded, source.subtitles)
            assertEquals(
                server
                    .url(
                        "/v2/files/42/hls/media.m3u8?oauth_token=download-secret&subtitle_key=all&subtitle_languages=en%2Ctr",
                    ).toString(),
                source.url.value,
            )
            assertEquals("<redacted credential URL>", source.url.toString())
            assertEquals(
                "/v2/files/42?mp4_status=1&start_from=1",
                server.takeRequest().target,
            )
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `resolvePlayback returns MP4 and wraps sidecar subtitle URLs`() =
        withServer { server ->
            server.enqueue(playbackFileResponse(isMp4Available = true, needConvert = false))
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "subtitles": [{
                            "format": "vtt",
                            "key": "en",
                            "language": "English",
                            "language_code": "en",
                            "name": "English",
                            "source": "opensubtitles",
                            "url": "https://media.example/subtitle.vtt?oauth_token=subtitle-secret"
                          }]
                        }
                        """.trimIndent(),
                    ).build(),
            )

            val resolution =
                server.resolvePlayback(
                    PlaybackRequest(
                        fileId = 42,
                        mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                        preference = PlaybackPreference.MP4,
                        useStartFrom = true,
                        subtitleLanguages = listOf("en", "tr"),
                    ),
                )
            val source = assertIs<PlaybackResolution.Ready>(resolution).source

            assertEquals(PlaybackSourceKind.MP4, source.kind)
            assertEquals(
                server.url("/v2/files/42/mp4/stream?oauth_token=download-secret").toString(),
                source.url.value,
            )
            val subtitles = assertIs<PlaybackSubtitles.Sidecar>(source.subtitles).tracks
            assertEquals(1, subtitles.size)
            assertEquals("en", subtitles.single().languageCode)
            assertEquals(
                "<redacted credential URL>",
                subtitles
                    .single()
                    .url
                    .toString(),
            )
            assertEquals("/v2/files/42?mp4_status=1&start_from=1", server.takeRequest().target)
            assertEquals("/v2/files/42/subtitles?languages=en%2Ctr", server.takeRequest().target)
        }

    @Test
    fun `resolvePlayback returns original video with exact URL resume and sidecars`() =
        withServer { server ->
            server.enqueue(playbackFileResponse(needConvert = true, startFrom = 12.5))
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"status":"OK","subtitles":[{
                          "key":"tr","language":"Turkish","language_code":"tr",
                          "name":"Turkish","source":"opensubtitles",
                          "url":"https://media.example/tr.vtt?oauth_token=subtitle-secret"
                        }]}
                        """.trimIndent(),
                    ).build(),
            )

            val source =
                assertIs<PlaybackResolution.Ready>(
                    server.resolvePlayback(
                        PlaybackRequest(
                            fileId = 42,
                            mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                            preference = PlaybackPreference.HLS,
                            useStartFrom = true,
                            capabilities = PlaybackCapabilities(originalVideoPlayable = true),
                            subtitleLanguages = listOf("tr"),
                        ),
                    ),
                ).source

            assertEquals(PlaybackSourceKind.ORIGINAL, source.kind)
            assertEquals(12.5, source.startFromSeconds)
            assertEquals(
                server.url("/v2/files/42/stream?oauth_token=download-secret").toString(),
                source.url.value,
            )
            val subtitles = assertIs<PlaybackSubtitles.Sidecar>(source.subtitles).tracks
            assertEquals("tr", subtitles.single().languageCode)
            assertEquals("<redacted credential URL>", subtitles.single().url.toString())
            assertEquals("/v2/files/42?mp4_status=1&start_from=1", server.takeRequest().target)
            assertEquals("/v2/files/42/subtitles?languages=tr", server.takeRequest().target)
        }

    @Test
    fun `resolvePlayback covers the valid video selection matrix`() {
        val cases =
            listOf(
                PlaybackSelectionCase(PlaybackPreference.HLS, false, false, false, PlaybackSourceKind.HLS),
                PlaybackSelectionCase(PlaybackPreference.HLS, false, false, true, null),
                PlaybackSelectionCase(PlaybackPreference.HLS, false, true, false, PlaybackSourceKind.HLS),
                PlaybackSelectionCase(PlaybackPreference.HLS, false, true, true, null),
                PlaybackSelectionCase(PlaybackPreference.MP4, false, true, false, PlaybackSourceKind.MP4),
                PlaybackSelectionCase(PlaybackPreference.MP4, false, true, true, PlaybackSourceKind.MP4),
                PlaybackSelectionCase(PlaybackPreference.MP4, false, false, false, PlaybackSourceKind.HLS),
                PlaybackSelectionCase(PlaybackPreference.MP4, false, false, true, null),
                PlaybackSelectionCase(PlaybackPreference.HLS, true, false, false, PlaybackSourceKind.ORIGINAL),
                PlaybackSelectionCase(PlaybackPreference.HLS, true, false, true, PlaybackSourceKind.ORIGINAL),
                PlaybackSelectionCase(PlaybackPreference.HLS, true, true, false, PlaybackSourceKind.ORIGINAL),
                PlaybackSelectionCase(PlaybackPreference.HLS, true, true, true, PlaybackSourceKind.ORIGINAL),
                PlaybackSelectionCase(PlaybackPreference.MP4, true, false, false, PlaybackSourceKind.ORIGINAL),
                PlaybackSelectionCase(PlaybackPreference.MP4, true, false, true, PlaybackSourceKind.ORIGINAL),
                PlaybackSelectionCase(PlaybackPreference.MP4, true, true, false, PlaybackSourceKind.ORIGINAL),
                PlaybackSelectionCase(PlaybackPreference.MP4, true, true, true, PlaybackSourceKind.ORIGINAL),
            )

        cases.forEach { case ->
            withServer { server ->
                server.enqueue(
                    playbackFileResponse(
                        isMp4Available = case.isMp4Available,
                        needConvert = case.needConvert,
                    ),
                )
                if (case.expectedKind == null) {
                    server.enqueue(
                        MockResponse
                            .Builder()
                            .body("""{"status":"OK","mp4":{"status":"IN_QUEUE","percent_done":0}}""")
                            .build(),
                    )
                }

                val resolution =
                    server.resolvePlayback(
                        PlaybackRequest(
                            fileId = 42,
                            mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                            preference = case.preference,
                            useStartFrom = true,
                            capabilities =
                                PlaybackCapabilities(originalVideoPlayable = case.originalVideoPlayable),
                            includeSidecarSubtitles = false,
                        ),
                    )

                if (case.expectedKind == null) {
                    assertEquals(
                        PlaybackConversionState.Queued,
                        assertIs<PlaybackResolution.Conversion>(resolution).state,
                    )
                    assertEquals(2, server.requestCount)
                } else {
                    assertEquals(case.expectedKind, assertIs<PlaybackResolution.Ready>(resolution).source.kind)
                    assertEquals(1, server.requestCount)
                }
            }
        }
    }

    @Test
    fun `resolvePlayback applies the account-wide resume preference`() =
        withServer { server ->
            server.enqueue(playbackFileResponse(needConvert = false, startFrom = 42.5))

            val source =
                assertIs<PlaybackResolution.Ready>(
                    server.resolvePlayback(
                        PlaybackRequest(
                            fileId = 42,
                            mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                            preference = PlaybackPreference.HLS,
                            useStartFrom = false,
                        ),
                    ),
                ).source

            assertEquals(0.0, source.startFromSeconds)
        }

    @Test
    fun `resolvePlayback keeps optional subtitle failures non-blocking but surfaces authentication`() {
        withServer { server ->
            server.enqueue(playbackFileResponse(isMp4Available = true, needConvert = false))
            server.enqueue(MockResponse.Builder().body("""{"status":"OK","subtitles":[]}""").build())

            val ready =
                assertIs<PlaybackResolution.Ready>(
                    server.resolvePlayback(
                        PlaybackRequest(
                            fileId = 42,
                            mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                            preference = PlaybackPreference.MP4,
                            useStartFrom = true,
                        ),
                    ),
                )

            assertEquals(PlaybackSubtitles.None, ready.source.subtitles)
        }

        withServer { server ->
            server.enqueue(playbackFileResponse(isMp4Available = true, needConvert = false))
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(500)
                    .body("""{"message":"subtitle service unavailable","status_code":500}""")
                    .build(),
            )

            val ready =
                assertIs<PlaybackResolution.Ready>(
                    server.resolvePlayback(
                        PlaybackRequest(
                            fileId = 42,
                            mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                            preference = PlaybackPreference.MP4,
                            useStartFrom = true,
                        ),
                    ),
                )

            assertEquals(
                PlaybackSubtitles.Unavailable(PlaybackSubtitleFailure.Rejected(500)),
                ready.source.subtitles,
            )
        }

        withServer { server ->
            server.enqueue(playbackFileResponse(isMp4Available = true, needConvert = false))
            server.enqueue(MockResponse.Builder().body("""{"status":"OK","subtitles":"invalid"}""").build())

            val ready =
                assertIs<PlaybackResolution.Ready>(
                    server.resolvePlayback(
                        PlaybackRequest(
                            fileId = 42,
                            mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                            preference = PlaybackPreference.MP4,
                            useStartFrom = true,
                        ),
                    ),
                )

            assertEquals(
                PlaybackSubtitles.Unavailable(PlaybackSubtitleFailure.InvalidResponse),
                ready.source.subtitles,
            )
        }

        withServer { server ->
            server.enqueue(playbackFileResponse(isMp4Available = true, needConvert = false))
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        if (chain
                                .request()
                                .url.encodedPath
                                .endsWith("/subtitles")
                        ) {
                            throw SocketTimeoutException("subtitle timeout")
                        }
                        chain.proceed(chain.request())
                    }.build()
            try {
                val ready =
                    assertIs<PlaybackResolution.Ready>(
                        server.resolvePlayback(
                            request =
                                PlaybackRequest(
                                    fileId = 42,
                                    mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                                    preference = PlaybackPreference.MP4,
                                    useStartFrom = true,
                                ),
                            okHttpClient = client,
                        ),
                    )

                assertIs<PlaybackSubtitleFailure.Transport>(
                    assertIs<PlaybackSubtitles.Unavailable>(ready.source.subtitles).failure,
                )
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }

        listOf(401, 403).forEach { statusCode ->
            withServer { server ->
                server.enqueue(playbackFileResponse(isMp4Available = true, needConvert = false))
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(statusCode)
                        .body(
                            """{"message":"expired","status_code":500,"error_type":"invalid_scope"}""",
                        ).build(),
                )

                val error =
                    assertFailsWith<PutioOperationException> {
                        server.resolvePlayback(
                            PlaybackRequest(
                                fileId = 42,
                                mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                                preference = PlaybackPreference.MP4,
                                useStartFrom = true,
                            ),
                        )
                    }

                assertEquals("resolvePlayback", error.operation)
            }
        }
    }

    @Test
    fun `resolvePlayback maps malformed subtitle credential URL to invalid response`() =
        withServer { server ->
            server.enqueue(playbackFileResponse(isMp4Available = true, needConvert = false))
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"status":"OK","subtitles":[{
                          "key":"en","language":"English","language_code":"en",
                          "name":"English","source":"opensubtitles","url":"not-a-url"
                        }]}
                        """.trimIndent(),
                    ).build(),
            )

            val ready =
                assertIs<PlaybackResolution.Ready>(
                    server.resolvePlayback(
                        PlaybackRequest(
                            fileId = 42,
                            mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                            preference = PlaybackPreference.MP4,
                            useStartFrom = true,
                        ),
                    ),
                )

            assertEquals(
                PlaybackSubtitles.Unavailable(PlaybackSubtitleFailure.InvalidResponse),
                ready.source.subtitles,
            )
        }

    @Test
    fun `resolvePlayback returns original audio and unsupported non media`() {
        withServer { server ->
            server.enqueue(playbackFileResponse(fileType = "AUDIO", needConvert = true))

            val source = assertIs<PlaybackResolution.Ready>(server.resolvePlayback()).source

            assertEquals(PlaybackSourceKind.ORIGINAL, source.kind)
            assertEquals(PlaybackSubtitles.None, source.subtitles)
            assertEquals(1, server.requestCount)
        }

        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"file":{"id":42,"file_type":"IMAGE"}}""")
                    .build(),
            )

            val unsupported = assertIs<PlaybackResolution.Unsupported>(server.resolvePlayback())

            assertEquals(PutioFileType.IMAGE, unsupported.fileType)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `resolvePlayback maps every conversion state without starting conversion`() {
        val cases =
            listOf(
                "IN_QUEUE" to PlaybackConversionState.Queued,
                "CONVERTING" to PlaybackConversionState.Converting(50.0),
                "COMPLETED" to PlaybackConversionState.Completed,
                "ERROR" to PlaybackConversionState.Failed,
                "NOT_AVAILABLE" to PlaybackConversionState.NotAvailable,
                "FUTURE_STATE" to PlaybackConversionState.Unknown("FUTURE_STATE", 50.0),
            )

        cases.forEach { (status, expected) ->
            withServer { server ->
                server.enqueue(playbackFileResponse(needConvert = true))
                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(
                            """{"status":"OK","mp4":{"status":"$status","percent_done":50}}""",
                        ).build(),
                )

                val conversion = assertIs<PlaybackResolution.Conversion>(server.resolvePlayback())

                assertEquals(expected, conversion.state)
                assertEquals("GET", server.takeRequest().method)
                val statusRequest = server.takeRequest()
                assertEquals("GET", statusRequest.method)
                assertEquals("/v2/files/42/mp4", statusRequest.target)
            }
        }
    }

    @Test
    fun `resolvePlayback preserves invalid conversion percent as unknown`() =
        withServer { server ->
            server.enqueue(playbackFileResponse(needConvert = true))
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"status":"OK","mp4":{"status":"CONVERTING","percent_done":101}}""")
                    .build(),
            )

            val conversion = assertIs<PlaybackResolution.Conversion>(server.resolvePlayback())

            assertEquals(PlaybackConversionState.Unknown("CONVERTING", 101.0), conversion.state)
        }

    @Test
    fun `resolvePlayback preserves converting state when progress is absent`() =
        withServer { server ->
            server.enqueue(playbackFileResponse(needConvert = true))
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"status":"OK","mp4":{"status":"CONVERTING"}}""")
                    .build(),
            )

            val conversion = assertIs<PlaybackResolution.Conversion>(server.resolvePlayback())

            assertEquals(PlaybackConversionState.Converting(null), conversion.state)
        }

    @Test
    fun `resolvePlayback rejects incomplete playback fields with operation context`() {
        listOf(
            """{"file":{"id":42,"file_type":"VIDEO","is_mp4_available":false,"start_from":0}}""",
        ).forEach { body ->
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(body)
                        .build(),
                )

                val error =
                    assertFailsWith<PutioOperationException> {
                        server.resolvePlayback()
                    }

                assertEquals("files", error.domain)
                assertEquals("resolvePlayback", error.operation)
                assertIs<PutioSerializationException>(error.underlyingError)
            }
        }
    }

    @Test
    fun `playback credential and aggregate debug strings stay redacted`() {
        val credential = PlaybackMediaCredential.downloadToken("download-secret")
        val source =
            PlaybackSource(
                fileId = 42,
                kind = PlaybackSourceKind.HLS,
                url = PutioCredentialUrl("https://media.example/video?oauth_token=download-secret"),
                startFromSeconds = 0.0,
                subtitles = PlaybackSubtitles.None,
            )
        val request =
            PlaybackRequest(
                fileId = 42,
                mediaCredential = credential,
                preference = PlaybackPreference.HLS,
                useStartFrom = true,
            )
        val resolution = PlaybackResolution.Ready(source)

        assertEquals("<redacted media credential>", credential.toString())
        assertEquals(false, request.toString().contains("download-secret"))
        assertEquals(false, source.toString().contains("download-secret"))
        assertEquals(false, resolution.toString().contains("download-secret"))

        val invalid = PutioCredentialUrl("download-secret")
        val error = assertFailsWith<IllegalArgumentException> { invalid.encodedPath }
        assertEquals("Invalid credential URL", error.message)
    }

    @Test
    fun `consumer-owned credential URLs validate at construction and stay redacted`() {
        val url = PutioCredentialUrl.of("https://api.put.io/v2/files/7/hls/media.m3u8?subtitle_key=all")
        assertEquals("/v2/files/7/hls/media.m3u8", url.encodedPath)
        assertEquals(setOf("subtitle_key"), url.queryParameterNames)
        assertEquals("<redacted credential URL>", url.toString())
        val error = assertFailsWith<IllegalArgumentException> { PutioCredentialUrl.of("not a url") }
        assertEquals("Invalid credential URL", error.message)
    }

    @Test
    fun `search uses files search endpoint`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "cursor": null,
                          "total": 1,
                          "files": [
                            {
                              "id": 2,
                              "name": "Example",
                              "size": 10,
                              "created_at": "2026-04-20T10:00:00Z",
                              "updated_at": "2026-04-20T10:00:00Z",
                              "file_type": "VIDEO",
                              "folder_type": "REGULAR"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val response =
                        sdk.files.search(
                            FilesSearchQuery(
                                keyword = "example",
                                perPage = 20,
                                type = listOf(PutioFileType.VIDEO, PutioFileType.AUDIO),
                            ),
                        )
                    assertEquals(1, response.total)
                    assertEquals("Example", response.files.first().name)
                }
            }

            assertEquals("/v2/files/search?query=example&per_page=20&type=VIDEO%2CAUDIO", server.takeRequest().target)
        }

    @Test
    fun `listSubtitles decodes subtitle payload`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "default": "en",
                          "subtitles": [
                            {
                              "key": "en",
                              "language": "English",
                              "language_code": "en",
                              "name": "English",
                              "source": "opensubtitles",
                              "url": "https://example.com/sub.srt"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val response = sdk.files.listSubtitles(5)
                    assertEquals("en", response.defaultKey)
                    assertEquals("https://example.com/sub.srt", response.subtitles.first().url)
                }
            }
        }

    @Test
    fun `findNextFile decodes playback-adjacent next media`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "next_file": {
                            "id": 42,
                            "name": "Next Episode.mkv",
                            "parent_id": 9,
                            "file_type": "VIDEO"
                          }
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val nextFile = sdk.files.findNextFile(fileId = 41, fileType = NextFileType.VIDEO)
                    assertEquals(42L, nextFile.id)
                    assertEquals("Next Episode.mkv", nextFile.name)
                    assertEquals(NextFileType.VIDEO, nextFile.fileType)
                }
            }

            assertEquals("/v2/files/41/next-file?file_type=VIDEO", server.takeRequest().target)
        }

    @Test
    fun `mp4 conversion endpoints decode forward-compatible status`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "mp4": {
                            "id": 10,
                            "percent_done": 25,
                            "status": "CONVERTING"
                          }
                        }
                        """.trimIndent(),
                    ).build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "status": "OK",
                          "mp4": {
                            "id": 10,
                            "percent_done": 100,
                            "size": 2048,
                            "status": "COMPLETED"
                          }
                        }
                        """.trimIndent(),
                    ).build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val started = sdk.files.startMp4Conversion(fileId = 10)
                    val status = sdk.files.getMp4ConversionStatus(fileId = 10)

                    assertEquals(FileMp4ConversionStatus.CONVERTING, started.status)
                    assertEquals(25.0, started.percentDone)
                    assertEquals(FileMp4ConversionStatus.COMPLETED, status.status)
                    assertEquals(2048L, status.size)
                }
            }

            val startRequest = server.takeRequest()
            val statusRequest = server.takeRequest()
            assertEquals("/v2/files/10/mp4", startRequest.target)
            assertEquals("/v2/files/10/mp4", statusRequest.target)
        }

    @Test
    fun `setStartFrom and resetStartFrom hit playback endpoints`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    sdk.files.setStartFrom(fileId = 10, time = 42.0)
                    sdk.files.resetStartFrom(fileId = 10)
                }
            }

            val setRequest = server.takeRequest()
            val resetRequest = server.takeRequest()
            assertEquals("/v2/files/10/start-from/set", setRequest.target)
            assertEquals("time=42.0", setRequest.body!!.utf8())
            assertEquals("/v2/files/10/start-from/delete", resetRequest.target)
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private fun MockWebServer.resolvePlayback(
        request: PlaybackRequest =
            PlaybackRequest(
                fileId = 42,
                mediaCredential = PlaybackMediaCredential.downloadToken("download-secret"),
                preference = PlaybackPreference.HLS,
                useStartFrom = true,
            ),
        okHttpClient: OkHttpClient? = null,
    ): PlaybackResolution =
        runBlocking {
            PutioClient(
                PutioConfig(
                    accessToken = "api-token",
                    baseUrl = url("/v2/").toString(),
                ),
                okHttpClient = okHttpClient,
            ).use { sdk -> sdk.files.resolvePlayback(request) }
        }

    private fun playbackFileResponse(
        fileType: String = "VIDEO",
        isMp4Available: Boolean = false,
        needConvert: Boolean,
        startFrom: Double = 0.0,
    ): MockResponse =
        MockResponse
            .Builder()
            .body(
                """
                {
                  "status": "OK",
                  "file": {
                    "id": 42,
                    "file_type": "$fileType",
                    "is_mp4_available": $isMp4Available,
                    "need_convert": $needConvert,
                    "start_from": $startFrom
                  }
                }
                """.trimIndent(),
            ).build()

    private data class PlaybackSelectionCase(
        val preference: PlaybackPreference,
        val originalVideoPlayable: Boolean,
        val isMp4Available: Boolean,
        val needConvert: Boolean,
        val expectedKind: PlaybackSourceKind?,
    )
}
