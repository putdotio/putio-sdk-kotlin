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

    suspend fun createFolder(
        name: String,
        parentId: Long,
    ): OkResponse =
        transport.post(
            path = "/files/create-folder",
            serializer = OkResponse.serializer(),
            form = mapOf(
                "name" to name,
                "parent_id" to parentId.toString(),
            ),
        )

    suspend fun delete(
        fileIds: List<Long>,
        skipNonexistents: Boolean = true,
        skipOwnerCheck: Boolean = false,
    ): OkResponse =
        transport.post(
            path = "/files/delete",
            serializer = OkResponse.serializer(),
            query = mapOf(
                "skip_nonexistents" to skipNonexistents.toString(),
                "skip_owner_check" to skipOwnerCheck.toString(),
            ),
            form = mapOf("file_ids" to fileIds.joinToString(",")),
        )

    suspend fun move(
        fileIds: List<Long>,
        parentId: Long,
    ): OkResponse =
        transport.post(
            path = "/files/move",
            serializer = OkResponse.serializer(),
            form = mapOf(
                "file_ids" to fileIds.joinToString(","),
                "parent_id" to parentId.toString(),
            ),
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

