package io.putdotio.sdk.account

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class AccountApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun getInfo(query: AccountInfoQuery = AccountInfoQuery()): AccountInfo =
        transport
            .get(
                path = "/account/info",
                serializer = AccountInfoEnvelope.serializer(),
                query = query.toQueryMap(),
            ).info

    suspend fun getSettings(): AccountSettings =
        transport
            .get(
                path = "/account/settings",
                serializer = AccountSettingsEnvelope.serializer(),
            ).settings

    suspend fun saveSettings(update: AccountSettingsUpdate): OkResponse =
        transport.postJson(
            path = "/account/settings",
            serializer = OkResponse.serializer(),
            body = update,
            bodySerializer = AccountSettingsUpdate.serializer(),
        )

    suspend fun clearData(options: AccountClearOptions): OkResponse =
        putioOperation(CLEAR_ACCOUNT_ERROR_SPEC) {
            transport.postJson(
                path = "/account/clear",
                serializer = OkResponse.serializer(),
                body = options,
                bodySerializer = AccountClearOptions.serializer(),
            )
        }

    suspend fun destroy(currentPassword: String): OkResponse =
        putioOperation(DESTROY_ACCOUNT_ERROR_SPEC) {
            transport.postJson(
                path = "/account/destroy",
                serializer = OkResponse.serializer(),
                body = AccountDestroyInput(currentPassword),
                bodySerializer = AccountDestroyInput.serializer(),
            )
        }
}

private val CLEAR_ACCOUNT_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "account",
        operation = "clearData",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
            ),
    )

private val DESTROY_ACCOUNT_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "account",
        operation = "destroy",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "INVALID_PASSWORD", statusCode = 400),
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 403),
            ),
    )
