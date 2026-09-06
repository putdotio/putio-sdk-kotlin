package io.putdotio.sdk.trash

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class TrashApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(query: TrashListQuery = TrashListQuery()): TrashListResponse =
        putioOperation(LIST_TRASH_ERROR_SPEC) {
            transport
                .get(
                    path = "/trash/list",
                    serializer = TrashListEnvelope.serializer(),
                    query = query.toQueryMap(),
                ).toResponse()
        }

    suspend fun continueList(
        cursor: String,
        query: TrashContinueQuery = TrashContinueQuery(),
    ): TrashListResponse =
        putioOperation(CONTINUE_TRASH_ERROR_SPEC) {
            transport
                .post(
                    path = "/trash/list/continue",
                    serializer = TrashContinueEnvelope.serializer(),
                    query = query.toQueryMap(),
                    form = mapOf("cursor" to cursor),
                ).toResponse()
        }

    suspend fun restore(input: TrashBulkInput): OkResponse =
        putioOperation(RESTORE_TRASH_ERROR_SPEC) {
            transport.post(
                path = "/trash/restore",
                serializer = OkResponse.serializer(),
                form = input.toFormMap(),
            )
        }

    suspend fun delete(input: TrashBulkInput): OkResponse =
        putioOperation(DELETE_TRASH_ERROR_SPEC) {
            transport.post(
                path = "/trash/delete",
                serializer = OkResponse.serializer(),
                form = input.toFormMap(),
            )
        }

    suspend fun empty(): OkResponse =
        putioOperation(EMPTY_TRASH_ERROR_SPEC) {
            transport.post(
                path = "/trash/empty",
                serializer = OkResponse.serializer(),
            )
        }
}

private val LIST_TRASH_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "trash",
        operation = "list",
    )

private val CONTINUE_TRASH_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "trash",
        operation = "continueList",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
            ),
    )

private val RESTORE_TRASH_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "trash",
        operation = "restore",
        knownErrors =
            listOf(
                PutioKnownErrorContract(statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val DELETE_TRASH_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "trash",
        operation = "delete",
        knownErrors =
            listOf(
                PutioKnownErrorContract(statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val EMPTY_TRASH_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "trash",
        operation = "empty",
    )
