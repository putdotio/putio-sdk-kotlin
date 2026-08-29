package io.putdotio.sdk.errors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
    responseBody: String,
    cause: Throwable,
) : PutioException(
        "Failed to parse response for ${request.method} ${request.redacted().url}",
        cause.redactedSerializationCause(),
    ) {
    val request: PutioRequestData = request.redacted()
    val responseBody: String = redactSensitiveUrlsInText(responseBody)
}

class PutioApiException(
    request: PutioRequestData,
    private val resolvedStatusCode: Int,
    private val resolvedErrorType: String?,
    envelope: PutioApiErrorEnvelope,
    responseBody: String,
    message: String,
) : PutioException(redactSensitiveUrlsInText(message)) {
    val request: PutioRequestData = request.redacted()
    val envelope: PutioApiErrorEnvelope = envelope.copy(message = envelope.message?.let(::redactSensitiveUrlsInText))
    val responseBody: String = redactSensitiveUrlsInText(responseBody)

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

internal fun redactSensitiveUrlsInText(text: String): String {
    val literalUrlsRedacted = URL_IN_TEXT_REGEX.replace(text) { match -> redactSensitiveQueryValues(match.value) }
    return JSON_ESCAPED_URL_IN_TEXT_REGEX.replace(literalUrlsRedacted) { match ->
        val unescapedUrl = match.value.replace("\\/", "/")
        val redactedUrl = redactSensitiveQueryValues(unescapedUrl)
        if (redactedUrl == unescapedUrl) match.value else redactedUrl.replace("/", "\\/")
    }
}

private fun Throwable.redactedSerializationCause(): Throwable {
    val safeMessage = message?.let(::redactSensitiveUrlsInText)
    return SerializationException(listOfNotNull(javaClass.name, safeMessage).joinToString(": ")).also {
        it.stackTrace = stackTrace
    }
}

private fun String.isSensitiveQueryParameterName(): Boolean {
    val words =
        replace(ACRONYM_WORD_BOUNDARY_REGEX, "$1_$2")
            .replace(CAMEL_CASE_WORD_BOUNDARY_REGEX, "$1_$2")
            .lowercase()
            .split(NON_ALPHANUMERIC_REGEX)
            .filter(String::isNotEmpty)
    val compact = words.joinToString(separator = "")

    if (compact in EXACT_SENSITIVE_QUERY_PARAMETER_NAMES) {
        return true
    }

    return words.any { it in SENSITIVE_QUERY_PARAMETER_WORDS } ||
        words.windowed(size = 2).any { it == API_KEY_WORDS }
}

private const val REDACTED_QUERY_VALUE = "REDACTED"

private val URL_IN_TEXT_REGEX = Regex("""https?://[^\s<>\"']*[A-Za-z0-9_~/%=&+\-]""", RegexOption.IGNORE_CASE)
private val JSON_ESCAPED_URL_IN_TEXT_REGEX =
    Regex("""https?:\\/\\/[^\s<>\"']*[A-Za-z0-9_~\\/%=&+\-]""", RegexOption.IGNORE_CASE)
private val ACRONYM_WORD_BOUNDARY_REGEX = Regex("([A-Z]+)([A-Z][a-z])")
private val CAMEL_CASE_WORD_BOUNDARY_REGEX = Regex("([a-z0-9])([A-Z])")
private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")

private val EXACT_SENSITIVE_QUERY_PARAMETER_NAMES =
    setOf(
        "accesskey",
        "accesskeyid",
        "apikey",
        "auth",
        "authcode",
        "authorization",
        "authorizationcode",
        "awsaccesskeyid",
        "code",
        "key",
        "nonce",
        "oauthcode",
        "session",
        "sessionid",
        "sig",
    )

private val SENSITIVE_QUERY_PARAMETER_WORDS =
    setOf(
        "authorization",
        "credential",
        "nonce",
        "password",
        "passwd",
        "secret",
        "session",
        "signature",
        "token",
    )

private val API_KEY_WORDS = listOf("api", "key")
