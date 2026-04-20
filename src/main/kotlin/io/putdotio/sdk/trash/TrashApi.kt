package io.putdotio.sdk.trash

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport

class TrashApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(query: TrashListQuery = TrashListQuery()): TrashListResponse =
        transport.get(
            path = "/trash/list",
            serializer = TrashListResponse.serializer(),
            query = query.toQueryMap(),
        )

    suspend fun restore(input: TrashBulkInput): OkResponse =
        transport.post(
            path = "/trash/restore",
            serializer = OkResponse.serializer(),
            form = input.toFormMap(),
        )

    suspend fun delete(input: TrashBulkInput): OkResponse =
        transport.post(
            path = "/trash/delete",
            serializer = OkResponse.serializer(),
            form = input.toFormMap(),
        )

    suspend fun empty(): OkResponse =
        transport.post(
            path = "/trash/empty",
            serializer = OkResponse.serializer(),
        )
}
