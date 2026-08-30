package io.putdotio.sdk.config

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class ConfigApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun get(): AppConfig =
        putioOperation(GET_CONFIG_ERROR_SPEC) {
            AppConfig(
                transport
                    .get(
                        path = "/config",
                        serializer = AppConfigEnvelope.serializer(),
                    ).config,
            )
        }

    suspend fun save(update: AppConfigUpdate): OkResponse =
        putioOperation(SAVE_CONFIG_ERROR_SPEC) {
            transport.putJson(
                pathSegments = listOf("config", update.key),
                serializer = OkResponse.serializer(),
                body = ConfigValueUpdateBody(update.value),
                bodySerializer = ConfigValueUpdateBody.serializer(),
            )
        }
}

private val GET_CONFIG_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "config",
        operation = "get",
        knownErrors = listOf(PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401)),
    )

private val SAVE_CONFIG_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "config",
        operation = "save",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
            ),
    )
