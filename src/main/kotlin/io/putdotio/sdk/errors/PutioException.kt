package io.putdotio.sdk.errors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
    request: PutioRequestData,
    cause: Throwable,
) : PutioException("Transport failure for ${request.method} ${request.redacted().url}", cause) {
    val request: PutioRequestData = request.redacted()
}

class PutioSerializationException(
    request: PutioRequestData,
    val responseBody: String,
    cause: Throwable,
) : PutioException("Failed to parse response for ${request.method} ${request.redacted().url}", cause) {
    val request: PutioRequestData = request.redacted()
}

class PutioApiException(
    request: PutioRequestData,
    private val resolvedStatusCode: Int,
    private val resolvedErrorType: String?,
    val envelope: PutioApiErrorEnvelope,
    val responseBody: String,
    message: String,
) : PutioException(message) {
    val request: PutioRequestData = request.redacted()

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

internal fun PutioRequestData.redacted(): PutioRequestData {
    val redactedUrl = redactSensitiveQueryValues(url)
    return if (redactedUrl == url) this else copy(url = redactedUrl)
}

internal fun redactSensitiveQueryValues(url: String): String {
    val parsedUrl = url.toHttpUrlOrNull() ?: return url
    if (parsedUrl.querySize == 0) {
        return url
    }

    val redactedUrl = parsedUrl.newBuilder().query(null)
    var changed = false

    repeat(parsedUrl.querySize) { index ->
        val name = parsedUrl.queryParameterName(index)
        val value = parsedUrl.queryParameterValue(index)
        if (name.isSensitiveQueryParameterName()) {
            redactedUrl.addQueryParameter(name, REDACTED_QUERY_VALUE)
            changed = true
        } else {
            redactedUrl.addQueryParameter(name, value)
        }
    }

    return if (changed) redactedUrl.build().toString() else url
}

private fun String.isSensitiveQueryParameterName(): Boolean {
    val normalized = lowercase().replace('-', '_').replace('.', '_')
    if (normalized in EXACT_SENSITIVE_QUERY_PARAMETER_NAMES) {
        return true
    }

    return normalized.split('_').any { it in SENSITIVE_QUERY_PARAMETER_SEGMENTS } ||
        SENSITIVE_QUERY_PARAMETER_SEGMENTS.any(normalized::endsWith)
}

private const val REDACTED_QUERY_VALUE = "REDACTED"

private val EXACT_SENSITIVE_QUERY_PARAMETER_NAMES =
    setOf(
        "api_key",
        "apikey",
        "auth",
        "authorization",
        "authorization_code",
        "authorizationcode",
        "code",
        "key",
        "nonce",
        "session",
        "session_id",
        "sessionid",
        "sig",
    )

private val SENSITIVE_QUERY_PARAMETER_SEGMENTS =
    setOf(
        "credential",
        "password",
        "passwd",
        "secret",
        "signature",
        "token",
    )
