package io.putdotio.sdk.routes

import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class RoutesApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun list(): List<TunnelRoute> =
        putioOperation(LIST_ROUTES_ERROR_SPEC) {
            transport
                .get(
                    path = "/tunnel/routes",
                    serializer = RoutesEnvelope.serializer(),
                ).routes
        }
}

private val LIST_ROUTES_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "routes",
        operation = "list",
        knownErrors = listOf(PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401)),
    )
