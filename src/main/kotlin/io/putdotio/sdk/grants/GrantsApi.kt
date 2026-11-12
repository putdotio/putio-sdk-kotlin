package io.putdotio.sdk.grants

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation
import io.putdotio.sdk.files.PutioCredentialUrl

class GrantsApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(): List<OAuthGrant> =
        putioOperation(LIST_GRANTS_ERROR_SPEC) {
            transport
                .get(
                    path = "/oauth/grants",
                    serializer = GrantsEnvelope.serializer(),
                ).apps
        }

    suspend fun revoke(id: Long): OkResponse =
        putioOperation(REVOKE_GRANT_ERROR_SPEC) {
            transport.post(
                path = "/oauth/grants/$id/delete",
                serializer = OkResponse.serializer(),
            )
        }

    suspend fun clients(): List<OAuthClient> =
        putioOperation(LIST_CLIENTS_ERROR_SPEC) {
            transport
                .get(
                    path = "/oauth/clients",
                    serializer = ClientsEnvelope.serializer(),
                ).clients
        }

    suspend fun revokeClient(id: Long): OkResponse =
        putioOperation(REVOKE_CLIENT_ERROR_SPEC) {
            transport.post(
                path = "/oauth/clients/$id/delete",
                serializer = OkResponse.serializer(),
            )
        }

    /**
     * The icon put.io stores for app [appId], when its grant reports `has_icon`. The URL carries
     * [accessToken], so it prints redacted.
     */
    fun buildIconUrl(
        appId: Long,
        accessToken: String,
    ): PutioCredentialUrl =
        PutioCredentialUrl.of(
            transport.buildUrl(
                path = "/oauth/apps/$appId/icon",
                query = mapOf("oauth_token" to accessToken),
            ),
        )

    suspend fun linkDevice(code: String): OAuthGrant =
        putioOperation(LINK_DEVICE_ERROR_SPEC) {
            transport
                .post(
                    path = "/oauth2/oob/code",
                    serializer = GrantEnvelope.serializer(),
                    form = mapOf("code" to code),
                ).app
        }
}

private val LIST_GRANTS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "grants",
        operation = "list",
        knownErrors = listOf(PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401)),
    )

private val LIST_CLIENTS_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "grants",
        operation = "clients",
        knownErrors = listOf(PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401)),
    )

private val REVOKE_GRANT_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "grants",
        operation = "revoke",
        knownErrors = listOf(PutioKnownErrorContract(statusCode = 404)),
    )

private val REVOKE_CLIENT_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "grants",
        operation = "revokeClient",
        knownErrors = listOf(PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401)),
    )

private val LINK_DEVICE_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "grants",
        operation = "linkDevice",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "INVALID_CODE", statusCode = 400),
                PutioKnownErrorContract(statusCode = 404),
            ),
    )
