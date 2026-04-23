package io.putdotio.sdk.history

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class HistoryApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(query: HistoryListQuery = HistoryListQuery()): HistoryListResponse =
        putioOperation(LIST_EVENTS_ERROR_SPEC) {
            transport.get(
                path = "/events/list",
                serializer = HistoryListResponse.serializer(),
                query = query.toQueryMap(),
            )
        }

    suspend fun delete(eventId: Long): OkResponse =
        putioOperation(DELETE_EVENT_ERROR_SPEC) {
            transport.post(
                path = "/events/delete/$eventId",
                serializer = OkResponse.serializer(),
            )
        }

    suspend fun clear(): OkResponse =
        putioOperation(CLEAR_EVENTS_ERROR_SPEC) {
            transport.post(
                path = "/events/delete",
                serializer = OkResponse.serializer(),
            )
        }
}

private val LIST_EVENTS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "events",
        operation = "list",
        knownErrors = listOf(
            PutioKnownErrorContract(errorType = "INVALID_PER_PAGE", statusCode = 400),
            PutioKnownErrorContract(statusCode = 400),
        ),
    )

private val DELETE_EVENT_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "events",
        operation = "delete",
        knownErrors = listOf(PutioKnownErrorContract(statusCode = 401)),
    )

private val CLEAR_EVENTS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "events",
        operation = "clear",
        knownErrors = listOf(PutioKnownErrorContract(statusCode = 401)),
    )
