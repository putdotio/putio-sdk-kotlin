package io.putdotio.sdk.account

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioAuth
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class AccountApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun getInfo(query: AccountInfoQuery = AccountInfoQuery()): AccountInfo = getInfo(query, PutioAuth.ConfigToken)

    // Device-code linking validates a token before the caller has stored it in config.
    internal suspend fun getInfoWith(token: String): AccountInfo = getInfo(AccountInfoQuery(), PutioAuth.Token(token))

    private suspend fun getInfo(
        query: AccountInfoQuery,
        auth: PutioAuth,
    ): AccountInfo =
        putioOperation(GET_ACCOUNT_INFO_ERROR_SPEC) {
            transport
                .get(
                    path = "/account/info",
                    serializer = AccountInfoEnvelope.serializer(),
                    query = query.toQueryMap(),
                    auth = auth,
                ).info
        }

    suspend fun getSettings(): AccountSettings =
        putioOperation(GET_ACCOUNT_SETTINGS_ERROR_SPEC) {
            transport
                .get(
                    path = "/account/settings",
                    serializer = AccountSettingsEnvelope.serializer(),
                ).settings
        }

    suspend fun saveSettings(update: AccountSettingsUpdate): OkResponse =
        putioOperation(SAVE_ACCOUNT_SETTINGS_ERROR_SPEC) {
            transport.postJson(
                path = "/account/settings",
                serializer = OkResponse.serializer(),
                body = update,
                bodySerializer = AccountSettingsUpdate.serializer(),
            )
        }

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

private val GET_ACCOUNT_INFO_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "account",
        operation = "getInfo",
    )

private val GET_ACCOUNT_SETTINGS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "account",
        operation = "getSettings",
    )

private val SAVE_ACCOUNT_SETTINGS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "account",
        operation = "saveSettings",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "INVALID_CURRENT_PASSWORD", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_NEW_PASSWORD", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_USERNAME", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_MAIL", statusCode = 400),
                PutioKnownErrorContract(errorType = "DISPOSABLE_MAIL_NOT_ALLOWED", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_CALLBACK_URL", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_PUSHOVER_TOKEN", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_CODE", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_ENABLE", statusCode = 400),
                PutioKnownErrorContract(errorType = "INVALID_VALUE", statusCode = 400),
                PutioKnownErrorContract(errorType = "PWNED_NEW_PASSWORD", statusCode = 400),
                PutioKnownErrorContract(errorType = "SAME_USERNAME", statusCode = 409),
                PutioKnownErrorContract(errorType = "EXISTING_MAIL", statusCode = 409),
                PutioKnownErrorContract(errorType = "USERNAME_EXISTS", statusCode = 400),
                PutioKnownErrorContract(errorType = "UNAVAILABLE_VALUE", statusCode = 403),
                PutioKnownErrorContract(errorType = "ALREADY_ENABLED", statusCode = 403),
                PutioKnownErrorContract(errorType = "INVALID_STATE", statusCode = 403),
                PutioKnownErrorContract(errorType = "NOT_ENABLED", statusCode = 403),
            ),
    )

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
