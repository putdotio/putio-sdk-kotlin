package io.putdotio.sdk.files

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

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
            transport.get(
                path = "/files/$fileId",
                serializer = FileEnvelope.serializer(),
                query = query.toQueryMap(),
            ).file
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
            transport.post(
                path = "/files/create-folder",
                serializer = FileEnvelope.serializer(),
                form = mapOf(
                    "name" to name,
                    "parent_id" to parentId.toString(),
                ),
            ).file
        }

    suspend fun delete(
        fileIds: List<Long>,
        skipNonexistents: Boolean = true,
        skipOwnerCheck: Boolean = false,
    ): FileDeleteResult =
        putioOperation(DELETE_FILES_ERROR_SPEC) {
            transport.post(
                path = "/files/delete",
                serializer = FileDeleteResult.serializer(),
                query = mapOf(
                    "skip_nonexistents" to skipNonexistents.toString(),
                    "skip_owner_check" to skipOwnerCheck.toString(),
                ),
                form = mapOf("file_ids" to fileIds.joinToString(",")),
            )
        }

    suspend fun move(
        fileIds: List<Long>,
        parentId: Long,
    ): List<FileMoveError> =
        putioOperation(MOVE_FILES_ERROR_SPEC) {
            transport.post(
                path = "/files/move",
                serializer = FileMoveEnvelope.serializer(),
                form = mapOf(
                    "file_ids" to fileIds.joinToString(","),
                    "parent_id" to parentId.toString(),
                ),
            ).errors
        }

    suspend fun getStartFrom(fileId: Long): Double =
        putioOperation(START_FROM_ERROR_SPEC) {
            transport.get(
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
                query = languages.takeIf { it.isNotEmpty() }?.let { mapOf("languages" to it.joinToString(",")) }
                    ?: emptyMap(),
            )
        }

    fun buildDownloadUrl(
        fileId: Long,
        accessToken: String,
    ): String = transport.buildUrl(
        path = "/files/$fileId/download",
        query = mapOf("oauth_token" to accessToken),
    )

    fun buildHlsStreamUrl(
        fileId: Long,
        accessToken: String,
    ): String = transport.buildUrl(
        path = "/files/$fileId/hls/media.m3u8",
        query = mapOf(
            "oauth_token" to accessToken,
            "subtitle_key" to "all",
        ),
    )
}

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

private val SEARCH_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "search",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "SEARCH_TOO_LONG_QUERY", statusCode = 400),
            PutioKnownErrorContract(statusCode = 400),
        ),
    )

private val CREATE_FOLDER_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "createFolder",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "EMPTY_NAME", statusCode = 400),
            PutioKnownErrorContract(errorType = "SLASH_IN_NAME", statusCode = 400),
            PutioKnownErrorContract(errorType = "NAME_TOO_LONG", statusCode = 400),
            PutioKnownErrorContract(errorType = "NAME_ALREADY_EXIST", statusCode = 400),
            PutioKnownErrorContract(statusCode = 403),
            PutioKnownErrorContract(statusCode = 404),
        ),
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

private val START_FROM_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "startFrom",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "FEATURE_DISABLED", statusCode = 400),
            PutioKnownErrorContract(errorType = "INVALID_MEDIA", statusCode = 400),
            PutioKnownErrorContract(statusCode = 404),
        ),
    )

private val LIST_SUBTITLES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "files",
        operation = "listSubtitles",
        knownErrors = listOf(
            PutioKnownErrorContract(statusCode = 404),
            PutioKnownErrorContract(statusCode = 402),
        ),
    )
