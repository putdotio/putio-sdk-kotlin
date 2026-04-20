package io.putdotio.sdk.files

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport

class FilesApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(
        parentId: Long,
        query: FilesListQuery = FilesListQuery(),
    ): FilesListResponse =
        transport.get(
            path = "/files/list",
            serializer = FilesListResponse.serializer(),
            query = query.toQueryMap(parentId),
        )

    suspend fun get(
        fileId: Long,
        query: FileDetailsQuery = FileDetailsQuery(),
    ): PutioFile =
        transport.get(
            path = "/files/$fileId",
            serializer = FileEnvelope.serializer(),
            query = query.toQueryMap(),
        ).file

    suspend fun search(query: FilesSearchQuery): FileSearchResponse =
        transport.get(
            path = "/files/search",
            serializer = FileSearchResponse.serializer(),
            query = query.toQueryMap(),
        )

    suspend fun createFolder(
        name: String,
        parentId: Long,
    ): PutioFile =
        transport.post(
            path = "/files/create-folder",
            serializer = FileEnvelope.serializer(),
            form = mapOf(
                "name" to name,
                "parent_id" to parentId.toString(),
            ),
        ).file

    suspend fun delete(
        fileIds: List<Long>,
        skipNonexistents: Boolean = true,
        skipOwnerCheck: Boolean = false,
    ): FileDeleteResult =
        transport.post(
            path = "/files/delete",
            serializer = FileDeleteResult.serializer(),
            query = mapOf(
                "skip_nonexistents" to skipNonexistents.toString(),
                "skip_owner_check" to skipOwnerCheck.toString(),
            ),
            form = mapOf("file_ids" to fileIds.joinToString(",")),
        )

    suspend fun move(
        fileIds: List<Long>,
        parentId: Long,
    ): List<FileMoveError> =
        transport.post(
            path = "/files/move",
            serializer = FileMoveEnvelope.serializer(),
            form = mapOf(
                "file_ids" to fileIds.joinToString(","),
                "parent_id" to parentId.toString(),
            ),
        ).errors

    suspend fun getStartFrom(fileId: Long): Double =
        transport.get(
            path = "/files/$fileId/start-from",
            serializer = FileStartFromResponse.serializer(),
        ).startFrom

    suspend fun setStartFrom(
        fileId: Long,
        time: Double,
    ): OkResponse =
        transport.post(
            path = "/files/$fileId/start-from/set",
            serializer = OkResponse.serializer(),
            form = mapOf("time" to time.toString()),
        )

    suspend fun resetStartFrom(fileId: Long): OkResponse =
        transport.get(
            path = "/files/$fileId/start-from/delete",
            serializer = OkResponse.serializer(),
        )

    suspend fun listSubtitles(
        fileId: Long,
        languages: List<String> = emptyList(),
    ): FileSubtitlesResponse =
        transport.get(
            path = "/files/$fileId/subtitles",
            serializer = FileSubtitlesResponse.serializer(),
            query = languages.takeIf { it.isNotEmpty() }?.let { mapOf("languages" to it.joinToString(",")) }
                ?: emptyMap(),
        )

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
