package io.putdotio.sdk.errors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

data class PutioRequestData(
    val method: String,
    val url: String,
)

data class PutioKnownErrorContract(
    val errorType: String? = null,
    val statusCode: Int? = null,
)

data class PutioOperationErrorSpec(
    val domain: String,
    val operation: String,
    val knownErrors: List<PutioKnownErrorContract> = emptyList(),
)

@Serializable
data class PutioApiErrorEnvelope(
    val message: String? = null,
    val status: String? = null,
    @SerialName("status_code") val statusCode: Int? = null,
    @SerialName("error_type") val errorType: String? = null,
    val details: JsonElement? = null,
)

sealed class PutioException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class PutioConfigurationException(
    message: String,
) : PutioException(message)

class PutioTransportException(
    val request: PutioRequestData,
    cause: Throwable,
) : PutioException("Transport failure for ${request.method} ${request.url}", cause)

class PutioSerializationException(
    val request: PutioRequestData,
    val responseBody: String,
    cause: Throwable,
) : PutioException("Failed to parse response for ${request.method} ${request.url}", cause)

class PutioApiException(
    val request: PutioRequestData,
    private val resolvedStatusCode: Int,
    private val resolvedErrorType: String?,
    val envelope: PutioApiErrorEnvelope,
    val responseBody: String,
    message: String,
) : PutioException(message) {
    val statusCode: Int
        get() = envelope.statusCode ?: resolvedStatusCode

    val errorType: String?
        get() = envelope.errorType ?: resolvedErrorType
}

sealed interface PutioOperationErrorReason {
    data class ErrorType(
        val errorType: String,
    ) : PutioOperationErrorReason

    data class StatusCode(
        val statusCode: Int,
    ) : PutioOperationErrorReason
}

class PutioOperationException(
    val domain: String,
    val operation: String,
    val contract: PutioKnownErrorContract?,
    val reason: PutioOperationErrorReason?,
    val underlyingError: PutioException,
) : PutioException("put.io $domain.$operation failed", underlyingError)

internal suspend fun <T> putioOperation(
    spec: PutioOperationErrorSpec,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (error: PutioOperationException) {
        throw error
    } catch (error: PutioException) {
        throw spec.wrap(error)
    }

private fun PutioOperationErrorSpec.wrap(error: PutioException): PutioOperationException {
    val apiError = error as? PutioApiException
    val matchedContract = apiError?.let { findMatchingContract(it) }
    return PutioOperationException(
        domain = domain,
        operation = operation,
        contract = matchedContract,
        reason = matchedContract?.toReason(),
        underlyingError = error,
    )
}

private fun PutioOperationErrorSpec.findMatchingContract(error: PutioApiException): PutioKnownErrorContract? =
    knownErrors.firstOrNull { contract ->
        when {
            contract.errorType != null -> {
                error.errorType == contract.errorType &&
                    (contract.statusCode == null || error.statusCode == contract.statusCode)
            }

            contract.statusCode != null -> {
                error.statusCode == contract.statusCode
            }

            else -> {
                false
            }
        }
    }

private fun PutioKnownErrorContract.toReason(): PutioOperationErrorReason? =
    when {
        errorType != null -> PutioOperationErrorReason.ErrorType(errorType)
        statusCode != null -> PutioOperationErrorReason.StatusCode(statusCode)
        else -> null
    }
