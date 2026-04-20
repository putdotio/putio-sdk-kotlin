package io.putdotio.sdk.history

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport

class HistoryApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(query: HistoryListQuery = HistoryListQuery()): List<HistoryEvent> =
        transport.get(
            path = "/events/list",
            serializer = HistoryEventsEnvelope.serializer(),
            query = query.toQueryMap(),
        ).events

    suspend fun delete(eventId: Long): OkResponse =
        transport.post(
            path = "/events/delete/$eventId",
            serializer = OkResponse.serializer(),
        )

    suspend fun clear(): OkResponse =
        transport.post(
            path = "/events/delete",
            serializer = OkResponse.serializer(),
        )
}

