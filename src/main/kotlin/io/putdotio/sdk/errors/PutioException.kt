package io.putdotio.sdk.errors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

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

enum class PutioTransportFailureKind {
    TIMEOUT,
    INTERRUPTED,
    DNS,
    CONNECTION,
    TLS,
    PROTOCOL,
    IO,
    UNEXPECTED,
}

class PutioTransportException(
    request: PutioRequestData,
    cause: Throwable,
) : PutioException(
        "Transport failure for ${request.method} ${request.redacted().url}",
        cause.redactedTransportCause(),
    ) {
    val request: PutioRequestData = request.redacted()
    val failureKind: PutioTransportFailureKind = cause.toTransportFailureKind()
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
    resolvedErrorType: String?,
    envelope: PutioApiErrorEnvelope,
    responseBody: String,
    message: String,
) : PutioException(redactSensitiveUrlsInText(message)) {
    val request: PutioRequestData = request.redacted()
    val envelope: PutioApiErrorEnvelope = envelope.redacted()
    val responseBody: String = redactSensitiveUrlsInText(responseBody)
    private val resolvedErrorType: String? = resolvedErrorType?.let(::redactSensitiveUrlsInText)

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
    val parsedUrl = url.toHttpUrlOrNull() ?: return redactSensitiveQueryParametersInText(url)
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
    val escapedUrlsRedacted =
        JSON_ESCAPED_URL_IN_TEXT_REGEX.replace(literalUrlsRedacted) { match ->
            val unescapedUrl = JSON_ESCAPED_SLASH_REGEX.replace(match.value, "/")
            val redactedUrl = redactSensitiveQueryValues(unescapedUrl)
            if (redactedUrl == unescapedUrl) match.value else redactedUrl.replace("/", "\\/")
        }
    return redactSensitiveQueryParametersInText(escapedUrlsRedacted)
}

private fun PutioApiErrorEnvelope.redacted(): PutioApiErrorEnvelope =
    copy(
        message = message?.let(::redactSensitiveUrlsInText),
        status = status?.let(::redactSensitiveUrlsInText),
        errorType = errorType?.let(::redactSensitiveUrlsInText),
        details = details?.redacted(),
    )

private fun JsonElement.redacted(): JsonElement =
    when (this) {
        is JsonArray -> JsonArray(map(JsonElement::redacted))
        is JsonObject -> JsonObject(mapValues { (_, value) -> value.redacted() })
        is JsonPrimitive -> if (isString) JsonPrimitive(redactSensitiveUrlsInText(content)) else this
    }

private fun Throwable.redactedTransportCause(): Throwable = redactedDiagnosticCopy(depth = 0)

private fun Throwable.redactedSerializationCause(): Throwable =
    SerializationException(redactedDiagnosticMessage()).also {
        it.stackTrace = stackTrace
    }

private fun Throwable.redactedDiagnosticMessage(): String =
    listOfNotNull(javaClass.name, message?.let(::redactSensitiveUrlsInText)).joinToString(": ")

private fun Throwable.redactedDiagnosticCopy(depth: Int): Throwable {
    val safeMessage = message?.let(::redactSensitiveUrlsInText) ?: javaClass.name
    val copy = newDiagnosticCopy(safeMessage)
    copy.stackTrace = stackTrace

    if (depth < MAX_REDACTED_CAUSE_DEPTH) {
        cause?.takeUnless { it === this }?.let { nested ->
            copy.initCause(nested.redactedDiagnosticCopy(depth + 1))
        }
        suppressed.take(MAX_REDACTED_SUPPRESSED_EXCEPTIONS).forEach { suppressedError ->
            copy.addSuppressed(suppressedError.redactedDiagnosticCopy(depth + 1))
        }
    }

    return copy
}

private fun Throwable.newDiagnosticCopy(safeMessage: String): Throwable =
    when (this) {
        is SocketTimeoutException -> SocketTimeoutException(safeMessage)
        is UnknownHostException -> UnknownHostException(safeMessage)
        is NoRouteToHostException -> NoRouteToHostException(safeMessage)
        is ConnectException -> ConnectException(safeMessage)
        is SocketException -> SocketException(safeMessage)
        is SSLHandshakeException -> SSLHandshakeException(safeMessage)
        is SSLPeerUnverifiedException -> SSLPeerUnverifiedException(safeMessage)
        is SSLException -> SSLException(safeMessage)
        is ProtocolException -> ProtocolException(safeMessage)
        is InterruptedIOException -> InterruptedIOException(safeMessage)
        is IOException -> IOException(safeMessage)
        is RuntimeException -> RuntimeException(safeMessage)
        else -> Exception("${javaClass.name}: $safeMessage")
    }

private fun Throwable.toTransportFailureKind(): PutioTransportFailureKind =
    when (this) {
        is SocketTimeoutException -> PutioTransportFailureKind.TIMEOUT
        is UnknownHostException -> PutioTransportFailureKind.DNS
        is NoRouteToHostException, is ConnectException, is SocketException -> PutioTransportFailureKind.CONNECTION
        is SSLException -> PutioTransportFailureKind.TLS
        is ProtocolException -> PutioTransportFailureKind.PROTOCOL
        is InterruptedIOException -> PutioTransportFailureKind.INTERRUPTED
        is IOException -> PutioTransportFailureKind.IO
        else -> PutioTransportFailureKind.UNEXPECTED
    }

private fun redactSensitiveQueryParametersInText(text: String): String =
    QUERY_PARAMETER_IN_TEXT_REGEX.replace(text) { match ->
        val unicodeDecodedName =
            JSON_UNICODE_ESCAPE_REGEX.replace(match.groupValues[2]) { escaped ->
                escaped.groupValues[1]
                    .toInt(radix = 16)
                    .toChar()
                    .toString()
            }
        val decodingUrl =
            "$QUERY_PARAMETER_DECODING_BASE_URL?$unicodeDecodedName="
                .toHttpUrlOrNull()
        val name =
            if (decodingUrl != null && decodingUrl.querySize > 0) {
                decodingUrl.queryParameterName(0)
            } else {
                unicodeDecodedName
            }
        if (name.isSensitiveQueryParameterName()) {
            match.groupValues[1] + match.groupValues[2] + match.groupValues[3] + REDACTED_QUERY_VALUE
        } else {
            match.value
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
private const val QUERY_PARAMETER_DECODING_BASE_URL = "https://redaction.invalid/"
private const val MAX_REDACTED_CAUSE_DEPTH = 8
private const val MAX_REDACTED_SUPPRESSED_EXCEPTIONS = 8

private val URL_IN_TEXT_REGEX = Regex("""https?://[^\s<>\"']*[A-Za-z0-9_~/%=&+\-]""", RegexOption.IGNORE_CASE)
private val JSON_ESCAPED_URL_IN_TEXT_REGEX =
    Regex(
        """https?:(?:(?:\\/)|(?:\\u002f)){2}[^\s<>\"']*[A-Za-z0-9_~\\/%=&+\-]""",
        RegexOption.IGNORE_CASE,
    )
private val JSON_ESCAPED_SLASH_REGEX = Regex("""\\(?:/|u002f)""", RegexOption.IGNORE_CASE)
private val QUERY_PARAMETER_IN_TEXT_REGEX =
    Regex(
        """([?&]|\\u003f|\\u0026)((?:(?!\\u(?:003d|0026|003f))[^?&=#\s<>\"'])+)""" +
            """(=|\\u003d)((?:(?!\\u0026).)*?)(?=&|#|\s|[<>\"']|\\u0026|$)""",
        RegexOption.IGNORE_CASE,
    )
private val JSON_UNICODE_ESCAPE_REGEX = Regex("""\\u([0-9a-f]{4})""", RegexOption.IGNORE_CASE)
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
