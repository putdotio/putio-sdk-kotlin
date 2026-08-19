package io.putdotio.sdk.grants

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

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

private val REVOKE_GRANT_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "grants",
        operation = "revoke",
        knownErrors = listOf(PutioKnownErrorContract(statusCode = 404)),
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
