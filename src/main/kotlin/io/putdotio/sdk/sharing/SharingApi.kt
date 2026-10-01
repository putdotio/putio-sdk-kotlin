package io.putdotio.sdk.sharing

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class SharingApi internal constructor(
    private val transport: PutioTransport,
) {
    val publicShares = PublicSharesApi(transport)

    suspend fun shareFiles(input: ShareFilesInput): OkResponse =
        putioOperation(SHARE_FILES_ERROR_SPEC) {
            transport.post(
                path = "/files/share",
                serializer = OkResponse.serializer(),
                form = input.toFormMap(),
            )
        }

    suspend fun listSharedFiles(): List<SharedFile> =
        putioOperation(LIST_SHARED_FILES_ERROR_SPEC) {
            transport
                .get(
                    path = "/files/shared",
                    serializer = SharedFilesEnvelope.serializer(),
                ).shared
        }

    suspend fun getSharedWith(fileId: Long): SharedWith =
        putioOperation(GET_SHARED_WITH_ERROR_SPEC) {
            transport
                .get(
                    path = "/files/$fileId/shared-with-v2",
                    serializer = SharedWithEnvelope.serializer(),
                ).toSharedWith()
        }

    /**
     * Starts copying items shared with the viewer into their own folder and returns the copy's id
     * for [getCloneInfo]. put.io checks the selection and quotas when the copy runs, so a started
     * copy can still end in [SharedFileCloneStatus.ERROR].
     */
    suspend fun cloneSharedFiles(input: CloneSharedFilesInput): Long =
        putioOperation(CLONE_SHARED_FILES_ERROR_SPEC) {
            transport
                .post(
                    path = "/sharing/clone",
                    serializer = CloneSharedFilesEnvelope.serializer(),
                    form = input.toFormMap(),
                ).id
        }

    suspend fun getCloneInfo(id: Long): SharedFileCloneInfo {
        require(id > 0) { "Clone id must be positive" }
        return putioOperation(GET_CLONE_INFO_ERROR_SPEC) {
            transport
                .get(
                    path = "/sharing/clone/$id",
                    serializer = SharedFileCloneInfoEnvelope.serializer(),
                ).toInfo()
        }
    }

    /** Removes the given shares of [fileId]; share ids come from [getSharedWith]. */
    suspend fun unshare(
        fileId: Long,
        shareIds: List<Long>,
    ): OkResponse {
        require(shareIds.isNotEmpty()) { "unshare requires at least one share id; use unshareAll to remove every share" }
        return postUnshare(fileId, shareIds.joinToString(","))
    }

    /** Removes every share of [fileId], including an everyone share. */
    suspend fun unshareAll(fileId: Long): OkResponse = postUnshare(fileId, "everyone")

    private suspend fun postUnshare(
        fileId: Long,
        shares: String,
    ): OkResponse =
        putioOperation(UNSHARE_ERROR_SPEC) {
            transport.post(
                path = "/files/$fileId/unshare",
                serializer = OkResponse.serializer(),
                form = mapOf("shares" to shares),
            )
        }
}

class PublicSharesApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun create(fileId: Long): PublicShare =
        putioOperation(CREATE_PUBLIC_SHARE_ERROR_SPEC) {
            transport
                .post(
                    path = "/public_share/$fileId",
                    serializer = PublicShareEnvelope.serializer(),
                ).publicShare
        }

    suspend fun list(): List<PublicShare> =
        putioOperation(LIST_PUBLIC_SHARES_ERROR_SPEC) {
            transport
                .get(
                    path = "/public_share/list",
                    serializer = PublicSharesEnvelope.serializer(),
                ).publicShares
        }

    suspend fun delete(id: Long): OkResponse =
        putioOperation(DELETE_PUBLIC_SHARE_ERROR_SPEC) {
            transport.delete(
                path = "/public_share/$id",
                serializer = OkResponse.serializer(),
            )
        }
}

private val INVALID_SCOPE = PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401)

private val SHARE_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "shareFiles",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "ALREADY_SHARED", statusCode = 400),
                INVALID_SCOPE,
                PutioKnownErrorContract(statusCode = 400),
            ),
    )

private val CLONE_SHARED_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "cloneSharedFiles",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "SharedFileCloneConcurrentLimit", statusCode = 400),
                PutioKnownErrorContract(errorType = "SharedFileCloneTooManyFiles", statusCode = 400),
                PutioKnownErrorContract(errorType = "SharedFileCloneTooManyChildren", statusCode = 400),
                INVALID_SCOPE,
                PutioKnownErrorContract(statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val GET_CLONE_INFO_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "getCloneInfo",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "SHARED_FILE_CLONE_NOT_FOUND", statusCode = 404),
                INVALID_SCOPE,
            ),
    )

private val LIST_SHARED_FILES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "listSharedFiles",
        knownErrors = listOf(INVALID_SCOPE),
    )

private val GET_SHARED_WITH_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "getSharedWith",
        knownErrors = listOf(INVALID_SCOPE, PutioKnownErrorContract(statusCode = 404)),
    )

private val UNSHARE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "unshare",
        knownErrors =
            listOf(
                INVALID_SCOPE,
                PutioKnownErrorContract(statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val CREATE_PUBLIC_SHARE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "createPublicShare",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_NOT_ALLOWED_PLAN", statusCode = 403),
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_FOLDER_ROOT_NOT_ALLOWED", statusCode = 400),
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_SINGLE_FILE_LIMIT_EXCEEDED", statusCode = 403),
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_FOLDER_LINK_COUNT_LIMIT_EXCEEDED", statusCode = 403),
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_FOLDER_MAX_SIZE_LIMIT_EXCEEDED", statusCode = 403),
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_FOLDER_MAX_CHILDREN_LIMIT_EXCEEDED", statusCode = 403),
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_DAILY_TOTAL_LINK_COUNT_EXCEEDED", statusCode = 403),
                PutioKnownErrorContract(errorType = "PUBLIC_SHARE_WEEKLY_TOTAL_LINK_COUNT_EXCEEDED", statusCode = 403),
                INVALID_SCOPE,
                PutioKnownErrorContract(statusCode = 404),
            ),
    )

private val LIST_PUBLIC_SHARES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "listPublicShares",
        knownErrors = listOf(INVALID_SCOPE),
    )

private val DELETE_PUBLIC_SHARE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "sharing",
        operation = "deletePublicShare",
        knownErrors = listOf(INVALID_SCOPE, PutioKnownErrorContract(statusCode = 404)),
    )
