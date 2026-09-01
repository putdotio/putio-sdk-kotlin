package io.putdotio.sdk.files

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.PutioSerializationException
import io.putdotio.sdk.errors.PutioTransportException
import io.putdotio.sdk.errors.putioOperation
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class FilesApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(
        parentId: Long,
        query: FilesListQuery = FilesListQuery(),
    ): FilesListResponse =
        putioOperation(LIST_FILES_ERROR_SPEC) {
            transport.get(
                path = "/files/list",
                serializer = FilesListResponse.serializer(),
                query = query.toQueryMap(parentId),
            )
        }

    suspend fun get(
        fileId: Long,
        query: FileDetailsQuery = FileDetailsQuery(),
    ): PutioFile =
        putioOperation(GET_FILE_ERROR_SPEC) {
            transport
                .get(
                    path = "/files/$fileId",
                    serializer = FileEnvelope.serializer(),
                    query = query.toQueryMap(),
                ).file
        }

    suspend fun resolvePlayback(request: PlaybackRequest): PlaybackResolution =
        putioOperation(RESOLVE_PLAYBACK_ERROR_SPEC) {
            val file =
                transport
                    .get(
                        path = "/files/${request.fileId}",
                        serializer = PlaybackFileEnvelope.serializer(),
                        query =
                            mapOf(
                                "mp4_status" to "1",
                                "start_from" to "1",
                            ),
                    ).file

            val sourceKind = file.selectPlaybackSource(request)
            if (sourceKind == null) {
                return@putioOperation when (file.fileType) {
                    PutioFileType.AUDIO, PutioFileType.VIDEO -> {
                        val conversion =
                            transport
                                .get(
                                    path = "/files/${request.fileId}/mp4",
                                    serializer = FileMp4ConversionEnvelope.serializer(),
                                ).mp4
                        PlaybackResolution.Conversion(conversion.toPlaybackState())
                    }

                    else -> {
                        PlaybackResolution.Unsupported(file.fileType)
                    }
                }
            }

            val subtitles = resolvePlaybackSubtitles(file, sourceKind, request)

            PlaybackResolution.Ready(
                PlaybackSource(
                    fileId = request.fileId,
                    kind = sourceKind,
                    url = buildPlaybackUrl(request.fileId, sourceKind, request),
                    startFromSeconds = if (request.useStartFrom) requireNotNull(file.startFrom) else 0.0,
                    subtitles = subtitles,
                ),
            )
        }

    private suspend fun resolvePlaybackSubtitles(
        file: PlaybackFile,
        sourceKind: PlaybackSourceKind,
        request: PlaybackRequest,
    ): PlaybackSubtitles {
        if (sourceKind == PlaybackSourceKind.HLS) {
            return PlaybackSubtitles.Embedded
        }
        if (file.fileType != PutioFileType.VIDEO || !request.includeSidecarSubtitles) {
            return PlaybackSubtitles.None
        }

        return try {
            val subtitles =
                transport
                    .get(
                        path = "/files/${request.fileId}/subtitles",
                        serializer = FileSubtitlesResponse.serializer(),
                        query =
                            request.subtitleLanguages
                                .takeIf { it.isNotEmpty() }
                                ?.let { mapOf("languages" to it.joinToString(",")) }
                                ?: emptyMap(),
                    ).subtitles
            val tracks =
                subtitles.map { subtitle ->
                    subtitle.toPlaybackSubtitleOrNull()
                        ?: return PlaybackSubtitles.Unavailable(PlaybackSubtitleFailure.InvalidResponse)
                }
            if (tracks.isEmpty()) PlaybackSubtitles.None else PlaybackSubtitles.Sidecar(tracks)
        } catch (error: PutioApiException) {
            if (error.httpStatusCode == 401 || error.httpStatusCode == 403) throw error
            PlaybackSubtitles.Unavailable(PlaybackSubtitleFailure.Rejected(error.statusCode))
        } catch (error: PutioTransportException) {
            PlaybackSubtitles.Unavailable(PlaybackSubtitleFailure.Transport(error.failureKind))
        } catch (_: PutioSerializationException) {
            PlaybackSubtitles.Unavailable(PlaybackSubtitleFailure.InvalidResponse)
        }
    }

    suspend fun search(query: FilesSearchQuery): FileSearchResponse =
        putioOperation(SEARCH_FILES_ERROR_SPEC) {
            transport.get(
                path = "/files/search",
                serializer = FileSearchResponse.serializer(),
                query = query.toQueryMap(),
            )
        }

    suspend fun continueList(
        cursor: String,
        query: FilesContinueQuery = FilesContinueQuery(),
    ): FilesListResponse =
        putioOperation(LIST_FILES_ERROR_SPEC) {
            transport.post(
                path = "/files/list/continue",
                serializer = FilesListResponse.serializer(),
                query = query.toQueryMap(),
                form = mapOf("cursor" to cursor),
            )
        }

    suspend fun continueSearch(
        cursor: String,
        query: FilesContinueQuery = FilesContinueQuery(),
    ): FileSearchResponse =
        putioOperation(SEARCH_FILES_ERROR_SPEC) {
            transport.post(
                path = "/files/search/continue",
                serializer = FileSearchResponse.serializer(),
                query = query.toQueryMap(),
                form = mapOf("cursor" to cursor),
            )
        }

    suspend fun createFolder(
        name: String,
        parentId: Long,
    ): PutioFile =
        putioOperation(CREATE_FOLDER_ERROR_SPEC) {
            transport
                .post(
                    path = "/files/create-folder",
                    serializer = FileEnvelope.serializer(),
                    form =
                        mapOf(
                            "name" to name,
                            "parent_id" to parentId.toString(),
                        ),
                ).file
        }

    suspend fun copy(fileIds: List<Long>): OkResponse =
        putioOperation(COPY_FILES_ERROR_SPEC) {
            transport.post(
                path = "/files/copy-to-disk",
                serializer = OkResponse.serializer(),
                form = mapOf("file_ids" to fileIds.joinToString(",")),
            )
        }

    suspend fun delete(
        fileIds: List<Long>,
        skipNonexistents: Boolean = true,
        skipOwnerCheck: Boolean = false,
        skipTrash: Boolean? = null,
    ): FileDeleteResult =
        putioOperation(DELETE_FILES_ERROR_SPEC) {
            transport.post(
                path = "/files/delete",
                serializer = FileDeleteResult.serializer(),
                query =
                    buildMap {
                        put("skip_nonexistents", skipNonexistents.toString())
                        put("skip_owner_check", skipOwnerCheck.toString())
                        skipTrash?.let { put("skip_trash", it.toString()) }
                    },
                form = mapOf("file_ids" to fileIds.joinToString(",")),
            )
        }

    suspend fun move(
        fileIds: List<Long>,
        parentId: Long,
    ): List<FileMoveError> =
        putioOperation(MOVE_FILES_ERROR_SPEC) {
            transport
                .post(
                    path = "/files/move",
                    serializer = FileMoveEnvelope.serializer(),
                    form =
                        mapOf(
                            "file_ids" to fileIds.joinToString(","),
                            "parent_id" to parentId.toString(),
                        ),
                ).errors
        }

    suspend fun rename(
        fileId: Long,
        name: String,
    ): OkResponse =
        putioOperation(RENAME_FILE_ERROR_SPEC) {
            transport.post(
                path = "/files/rename",
                serializer = OkResponse.serializer(),
                form =
                    mapOf(
                        "file_id" to fileId.toString(),
                        "name" to name,
                    ),
            )
        }

    suspend fun findNextFile(
        fileId: Long,
        fileType: NextFileType,
    ): NextFile =
        putioOperation(FIND_NEXT_FILE_ERROR_SPEC) {
            transport
                .get(
                    path = "/files/$fileId/next-file",
                    serializer = NextFileEnvelope.serializer(),
                    query = mapOf("file_type" to fileType.raw),
                ).nextFile
        }

    suspend fun setSortBy(
        fileId: Long,
        sortBy: String,
    ): OkResponse =
        putioOperation(SET_SORT_BY_ERROR_SPEC) {
            transport.post(
                path = "/files/set-sort-by",
                serializer = OkResponse.serializer(),
                form =
                    mapOf(
                        "file_id" to fileId.toString(),
                        "sort_by" to sortBy,
                    ),
            )
        }

    suspend fun resetFileSpecificSortSettings(): OkResponse =
        putioOperation(RESET_SORT_BY_ERROR_SPEC) {
            transport.post(
                path = "/files/remove-sort-by-settings",
                serializer = OkResponse.serializer(),
            )
        }

    suspend fun startMp4Conversion(fileId: Long): FileMp4Conversion =
        putioOperation(MP4_CONVERSION_ERROR_SPEC) {
            transport
                .post(
                    path = "/files/$fileId/mp4",
                    serializer = FileMp4ConversionEnvelope.serializer(),
                ).mp4
        }

    suspend fun getMp4ConversionStatus(fileId: Long): FileMp4Conversion =
        putioOperation(MP4_CONVERSION_ERROR_SPEC) {
            transport
                .get(
                    path = "/files/$fileId/mp4",
                    serializer = FileMp4ConversionEnvelope.serializer(),
                ).mp4
        }

    suspend fun getStartFrom(fileId: Long): Double =
        putioOperation(START_FROM_ERROR_SPEC) {
            transport
                .get(
                    path = "/files/$fileId/start-from",
                    serializer = FileStartFromResponse.serializer(),
                ).startFrom
        }

    suspend fun setStartFrom(
        fileId: Long,
        time: Double,
    ): OkResponse =
        putioOperation(START_FROM_ERROR_SPEC) {
            transport.post(
                path = "/files/$fileId/start-from/set",
                serializer = OkResponse.serializer(),
                form = mapOf("time" to time.toString()),
            )
        }

    suspend fun resetStartFrom(fileId: Long): OkResponse =
        putioOperation(START_FROM_ERROR_SPEC) {
            transport.get(
                path = "/files/$fileId/start-from/delete",
                serializer = OkResponse.serializer(),
            )
        }

    suspend fun listSubtitles(
        fileId: Long,
        languages: List<String> = emptyList(),
    ): FileSubtitlesResponse =
        putioOperation(LIST_SUBTITLES_ERROR_SPEC) {
            transport.get(
                path = "/files/$fileId/subtitles",
                serializer = FileSubtitlesResponse.serializer(),
                query =
                    languages.takeIf { it.isNotEmpty() }?.let { mapOf("languages" to it.joinToString(",")) }
                        ?: emptyMap(),
            )
        }

    fun buildDownloadUrl(
        fileId: Long,
        accessToken: String,
    ): String =
        transport.buildUrl(
            path = "/files/$fileId/download",
            query = mapOf("oauth_token" to accessToken),
        )

    fun buildMp4DownloadUrl(
        fileId: Long,
        accessToken: String,
    ): String =
        transport.buildUrl(
            path = "/files/$fileId/mp4/download",
            query = mapOf("oauth_token" to accessToken),
        )

    internal fun buildMp4StreamUrl(
        fileId: Long,
        accessToken: String,
    ): String =
        transport.buildUrl(
            path = "/files/$fileId/mp4/stream",
            query = mapOf("oauth_token" to accessToken),
        )

    fun buildAudioStreamUrl(
        fileId: Long,
        accessToken: String,
    ): String = buildOriginalStreamUrl(fileId = fileId, accessToken = accessToken)

    internal fun buildOriginalStreamUrl(
        fileId: Long,
        accessToken: String,
    ): String =
        transport.buildUrl(
            path = "/files/$fileId/stream",
            query = mapOf("oauth_token" to accessToken),
        )

    fun buildStreamUrl(
        file: PutioFile,
        accessToken: String,
    ): String? =
        when (file.fileType) {
            PutioFileType.AUDIO -> buildAudioStreamUrl(fileId = file.id, accessToken = accessToken)
            PutioFileType.VIDEO -> buildHlsStreamUrl(fileId = file.id, accessToken = accessToken)
            else -> null
        }

    fun buildStreamUrl(
        nextFile: NextFile,
        accessToken: String,
    ): String? =
        when (nextFile.fileType) {
            NextFileType.AUDIO -> buildAudioStreamUrl(fileId = nextFile.id, accessToken = accessToken)
            NextFileType.VIDEO -> buildHlsStreamUrl(fileId = nextFile.id, accessToken = accessToken)
            else -> null
        }

    fun buildHlsStreamUrl(
        fileId: Long,
        accessToken: String,
        subtitleLanguages: List<String> = emptyList(),
    ): String =
        transport.buildUrl(
            path = "/files/$fileId/hls/media.m3u8",
            query =
                buildMap {
                    put("oauth_token", accessToken)
                    put("subtitle_key", "all")
                    if (subtitleLanguages.isNotEmpty()) {
                        put("subtitle_languages", subtitleLanguages.joinToString(","))
                    }
                },
        )
}

private fun PlaybackFile.selectPlaybackSource(request: PlaybackRequest): PlaybackSourceKind? =
    when (fileType) {
        PutioFileType.AUDIO -> {
            PlaybackSourceKind.ORIGINAL
        }

        PutioFileType.VIDEO -> {
            when {
                request.capabilities.originalVideoPlayable -> PlaybackSourceKind.ORIGINAL
                request.preference == PlaybackPreference.MP4 && isMp4Available == true -> PlaybackSourceKind.MP4
                needConvert == true -> null
                request.preference == PlaybackPreference.HLS -> PlaybackSourceKind.HLS
                else -> PlaybackSourceKind.HLS
            }
        }

        else -> {
            null
        }
    }

private fun FileMp4Conversion.toPlaybackState(): PlaybackConversionState =
    when (status) {
        FileMp4ConversionStatus.IN_QUEUE -> {
            PlaybackConversionState.Queued
        }

        FileMp4ConversionStatus.CONVERTING -> {
            val invalidPercent = percentDone?.takeUnless { it.isFinite() && it in 0.0..100.0 }
            if (invalidPercent != null) {
                PlaybackConversionState.Unknown(status.raw, percentDone)
            } else {
                PlaybackConversionState.Converting(percentDone)
            }
        }

        FileMp4ConversionStatus.COMPLETED -> {
            PlaybackConversionState.Completed
        }

        FileMp4ConversionStatus.ERROR -> {
            PlaybackConversionState.Failed
        }

        FileMp4ConversionStatus.NOT_AVAILABLE -> {
            PlaybackConversionState.NotAvailable
        }

        else -> {
            PlaybackConversionState.Unknown(status.raw, percentDone)
        }
    }

private fun FileSubtitle.toPlaybackSubtitleOrNull(): PlaybackSubtitle? {
    if (url.toHttpUrlOrNull() == null) return null
    return PlaybackSubtitle(
        format = format,
        key = key,
        language = language,
        languageCode = languageCode,
        name = name,
        source = source,
        url = PutioCredentialUrl(url),
    )
}

private fun FilesApi.buildPlaybackUrl(
    fileId: Long,
    kind: PlaybackSourceKind,
    request: PlaybackRequest,
): PutioCredentialUrl =
    PutioCredentialUrl(
        when (kind) {
            PlaybackSourceKind.ORIGINAL -> {
                buildOriginalStreamUrl(fileId, request.mediaCredential.value)
            }

            PlaybackSourceKind.HLS -> {
                buildHlsStreamUrl(
                    fileId = fileId,
                    accessToken = request.mediaCredential.value,
                    subtitleLanguages = request.subtitleLanguages,
                )
            }

            PlaybackSourceKind.MP4 -> {
                buildMp4StreamUrl(fileId, request.mediaCredential.value)
            }
        },
    )

private val LIST_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "list",
    )

private val GET_FILE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "get",
        knownErrors = listOf(PutioKnownErrorContract(statusCode = 404)),
    )

private val RESOLVE_PLAYBACK_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "resolvePlayback",
        knownErrors = listOf(PutioKnownErrorContract(statusCode = 404)),
    )

private val SEARCH_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "search",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "SEARCH_TOO_LONG_QUERY", statusCode = 400),
                PutioKnownErrorContract(statusCode = 400),
            ),
    )

private val CREATE_FOLDER_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "createFolder",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "EMPTY_NAME", statusCode = 400),
                PutioKnownErrorContract(errorType = "SLASH_IN_NAME", statusCode = 400),
                PutioKnownErrorContract(errorType = "NAME_TOO_LONG", statusCode = 400),
                PutioKnownErrorContract(errorType = "NAME_ALREADY_EXIST", statusCode = 400),
                PutioKnownErrorContract(statusCode = 403),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val COPY_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "copy",
    )

private val DELETE_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "delete",
    )

private val MOVE_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "move",
    )

private val RENAME_FILE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "rename",
    )

private val FIND_NEXT_FILE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "findNextFile",
        knownErrors =
            listOf(
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val SET_SORT_BY_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "setSortBy",
    )

private val RESET_SORT_BY_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "resetFileSpecificSortSettings",
    )

private val MP4_CONVERSION_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "mp4Conversion",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "FEATURE_DISABLED", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_MEDIA", statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val START_FROM_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "startFrom",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "FEATURE_DISABLED", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_MEDIA", statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val LIST_SUBTITLES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "listSubtitles",
        knownErrors =
            listOf(
                PutioKnownErrorContract(statusCode = 404),
                PutioKnownErrorContract(statusCode = 402),
            ),
    )
