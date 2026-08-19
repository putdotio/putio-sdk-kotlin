package io.putdotio.sdk.ifttt

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioKnownErrorContract
import io.putdotio.sdk.errors.PutioOperationErrorSpec
import io.putdotio.sdk.errors.putioOperation

class IftttApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun sendPlaybackEvent(input: IftttPlaybackEventInput): OkResponse =
        putioOperation(SEND_PLAYBACK_EVENT_ERROR_SPEC) {
            transport.postJson(
                path = "/ifttt-client/event",
                serializer = OkResponse.serializer(),
                body = input,
                bodySerializer = IftttPlaybackEventInput.serializer(),
            )
        }
}

private val SEND_PLAYBACK_EVENT_ERROR_SPEC =
    PutioOperationErrorSpec(
        domain = "ifttt",
        operation = "sendPlaybackEvent",
        knownErrors =
            listOf(
                PutioKnownErrorContract(errorType = "invalid_scope", statusCode = 401),
                PutioKnownErrorContract(statusCode = 400),
            ),
    )
