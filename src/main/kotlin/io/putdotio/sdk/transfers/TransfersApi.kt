package io.putdotio.sdk.transfers

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class TransfersApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(query: TransfersListQuery = TransfersListQuery()): TransfersListResponse =
        putioOperation(LIST_TRANSFERS_ERROR_SPEC) {
            transport.get(
                path = "/transfers/list",
                serializer = TransfersListResponse.serializer(),
                query = query.toQueryMap(),
            )
        }

    suspend fun continueList(
        cursor: String,
        query: TransfersListQuery = TransfersListQuery(),
    ): TransfersListResponse =
        putioOperation(LIST_TRANSFERS_ERROR_SPEC) {
            transport.post(
                path = "/transfers/list/continue",
                serializer = TransfersListResponse.serializer(),
                query = query.toQueryMap(),
                form = mapOf("cursor" to cursor),
            )
        }

    suspend fun get(id: Long): Transfer =
        putioOperation(GET_TRANSFER_ERROR_SPEC) {
            transport
                .get(
                    path = "/transfers/$id",
                    serializer = TransferEnvelope.serializer(),
                ).transfer
        }

    suspend fun count(): Int =
        putioOperation(COUNT_TRANSFERS_ERROR_SPEC) {
            transport
                .get(
                    path = "/transfers/count",
                    serializer = TransferCountEnvelope.serializer(),
                ).count
        }

    suspend fun info(urls: List<String>): TransferInfoResponse =
        putioOperation(INFO_TRANSFERS_ERROR_SPEC) {
            transport.post(
                path = "/transfers/info",
                serializer = TransferInfoResponse.serializer(),
                form = mapOf("urls" to urls.joinToString("\n")),
            )
        }

    suspend fun add(input: TransferAddInput): Transfer =
        putioOperation(ADD_TRANSFER_ERROR_SPEC) {
            transport
                .post(
                    path = "/transfers/add",
                    serializer = TransferEnvelope.serializer(),
                    form = input.toFormMap(),
                ).transfer
        }

    suspend fun addMany(inputs: List<TransferAddInput>): TransfersAddManyResponse =
        putioOperation(ADD_MANY_TRANSFERS_ERROR_SPEC) {
            val urls =
                PutioTransport.defaultJson.encodeToString(
                    ListSerializer(TransferAddInput.serializer()),
                    inputs,
                )

            transport.post(
                path = "/transfers/add-multi",
                serializer = TransfersAddManyResponse.serializer(),
                form = mapOf("urls" to urls),
            )
        }

    suspend fun cancel(ids: List<Long>): OkResponse =
        putioOperation(CANCEL_TRANSFERS_ERROR_SPEC) {
            transport.post(
                path = "/transfers/cancel",
                serializer = OkResponse.serializer(),
                form = mapOf("transfer_ids" to ids.joinToString(",")),
            )
        }

    suspend fun clean(ids: List<Long> = emptyList()): TransfersCleanResponse =
        putioOperation(CLEAN_TRANSFERS_ERROR_SPEC) {
            transport.post(
                path = "/transfers/clean",
                serializer = TransfersCleanResponse.serializer(),
                form =
                    ids.takeIf { it.isNotEmpty() }?.let { mapOf("transfer_ids" to it.joinToString(",")) }
                        ?: emptyMap(),
            )
        }

    suspend fun retry(id: Long): Transfer =
        putioOperation(RETRY_TRANSFER_ERROR_SPEC) {
            transport
                .post(
                    path = "/transfers/retry",
                    serializer = TransferEnvelope.serializer(),
                    form = mapOf("id" to id.toString()),
                ).transfer
        }
}

private val LIST_TRANSFERS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "list",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
            ),
    )

private val GET_TRANSFER_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "get",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val COUNT_TRANSFERS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "count",
    )

private val INFO_TRANSFERS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "info",
    )

private val ADD_TRANSFER_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "add",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "EMPTY_URL", statusCode = 400),
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val ADD_MANY_TRANSFERS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "addMany",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "TOO_MANY_URLS", statusCode = 403),
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
            ),
    )

private val CANCEL_TRANSFERS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "cancel",
    )

private val CLEAN_TRANSFERS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "clean",
    )

private val RETRY_TRANSFER_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "transfers",
        operation = "retry",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
                PutioKnownErrorContract(statusCode = 403),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )
