package io.putdotio.sdk.errors

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PutioExceptionTest {
    @Test
    fun `api exception falls back to resolved status and error type when envelope fields are absent`() {
        val error =
            PutioApiException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/1"),
                resolvedStatusCode = 418,
                resolvedErrorType = "I_AM_A_TEAPOT",
                envelope = PutioApiErrorEnvelope(message = "teapot"),
                responseBody = """{"message":"teapot"}""",
                message = "teapot",
            )

        assertEquals(418, error.statusCode)
        assertEquals("I_AM_A_TEAPOT", error.errorType)
    }

    @Test
    fun `putioOperation wraps matching api contracts with operation metadata`() {
        val error =
            assertFailsWith<PutioOperationException> {
                runBlocking {
                    putioOperation(
                        PutioOperationErrorSpec(
                            domain = "files",
                            operation = "createFolder",
                            knownErrors = listOf(PutioKnownErrorContract(errorType = "EMPTY_NAME", statusCode = 400)),
                        ),
                    ) {
                        throw PutioApiException(
                            request = PutioRequestData(method = "POST", url = "https://api.put.io/v2/files/create-folder"),
                            resolvedStatusCode = 400,
                            resolvedErrorType = "EMPTY_NAME",
                            envelope =
                                PutioApiErrorEnvelope(
                                    message = "name is required",
                                    statusCode = 400,
                                    errorType = "EMPTY_NAME",
                                ),
                            responseBody = """{"error_type":"EMPTY_NAME"}""",
                            message = "name is required",
                        )
                    }
                }
            }

        assertEquals("files", error.domain)
        assertEquals("createFolder", error.operation)
        assertEquals("EMPTY_NAME", error.contract?.errorType)
        val reason = assertIs<PutioOperationErrorReason.ErrorType>(error.reason)
        assertEquals("EMPTY_NAME", reason.errorType)
    }

    @Test
    fun `putioOperation preserves existing operation exceptions`() {
        val existing =
            PutioOperationException(
                domain = "auth",
                operation = "validateToken",
                contract = PutioKnownErrorContract(statusCode = 401),
                reason = PutioOperationErrorReason.StatusCode(401),
                underlyingError = PutioConfigurationException("Missing access token"),
            )

        val error =
            assertFailsWith<PutioOperationException> {
                runBlocking {
                    putioOperation(PutioOperationErrorSpec(domain = "auth", operation = "validateToken")) {
                        throw existing
                    }
                }
            }

        assertEquals(existing, error)
    }

    @Test
    fun `sdk exceptions redact credential query values and preserve request context`() {
        val request =
            PutioRequestData(
                method = "POST",
                url =
                    "https://api.put.io/v2/two_factor/verify/totp" +
                        "?oauth_token=oauth-secret" +
                        "&client_secret=client-secret" +
                        "&X-Amz-Credential=aws-secret" +
                        "&api-key=api-secret" +
                        "&accessToken=camel-secret" +
                        "&cursor=next-page" +
                        "&subtitle_key=all",
            )
        val expectedUrl =
            "https://api.put.io/v2/two_factor/verify/totp" +
                "?oauth_token=REDACTED" +
                "&client_secret=REDACTED" +
                "&X-Amz-Credential=REDACTED" +
                "&api-key=REDACTED" +
                "&accessToken=REDACTED" +
                "&cursor=next-page" +
                "&subtitle_key=all"
        val errors =
            listOf(
                PutioTransportException(request, IOException("offline")),
                PutioSerializationException(request, "{}", IllegalArgumentException("bad json")),
                PutioApiException(
                    request = request,
                    resolvedStatusCode = 400,
                    resolvedErrorType = "INVALID_CODE",
                    envelope =
                        PutioApiErrorEnvelope(
                            message = "request rejected",
                            statusCode = 400,
                            errorType = "INVALID_CODE",
                        ),
                    responseBody = "{}",
                    message = "request rejected",
                ),
            )

        errors.forEach { error ->
            val sdkError = assertIs<PutioException>(error)
            val redactedRequest =
                when (sdkError) {
                    is PutioTransportException -> sdkError.request
                    is PutioSerializationException -> sdkError.request
                    is PutioApiException -> sdkError.request
                    else -> error("Unexpected exception type")
                }
            val localized = PutioErrorLocalizer.localize(sdkError)

            assertEquals(expectedUrl, redactedRequest.url)
            assertEquals(expectedUrl, localized.meta["url"])
            listOf("oauth-secret", "client-secret", "aws-secret", "api-secret", "camel-secret").forEach { credential ->
                assertFalse(sdkError.message.orEmpty().contains(credential))
                assertFalse(localized.failureReason.contains(credential))
                assertFalse(localized.meta.values.any { it.contains(credential) })
            }
        }
    }

    @Test
    fun `query redaction preserves safe values and redacts malformed urls`() {
        val safeUrl = "https://api.put.io/v2/files/list?cursor=next-page&subtitle_key=all"
        val malformedUrl = "https://[broken]?oauth%5Ftoken=malformed-secret&cursor=next-page"

        assertEquals(safeUrl, redactSensitiveQueryValues(safeUrl))
        assertEquals("not a url", redactSensitiveQueryValues("not a url"))
        val redactedMalformedUrl = redactSensitiveQueryValues(malformedUrl)
        assertTrue(redactedMalformedUrl.contains("oauth%5Ftoken=REDACTED"))
        assertTrue(redactedMalformedUrl.contains("cursor=next-page"))
        assertFalse(redactedMalformedUrl.contains("malformed-secret"))
    }

    @Test
    fun `query redaction covers compound and camel case credential names`() {
        val sensitiveNames =
            listOf(
                "x_api_key",
                "oauth_authorization_code",
                "auth_code",
                "oauth_code",
                "AWSAccessKeyId",
                "client_session_id",
                "login_nonce",
                "tokenValue",
                "credentialId",
            )
        val url =
            sensitiveNames
                .mapIndexed { index, name -> "$name=secret-$index" }
                .joinToString(prefix = "https://api.put.io/v2/files/list?", separator = "&")

        val redacted = redactSensitiveQueryValues(url)

        sensitiveNames.forEach { name -> assertTrue(redacted.contains("$name=REDACTED")) }
        sensitiveNames.indices.forEach { index -> assertFalse(redacted.contains("secret-$index")) }
    }

    @Test
    fun `api exception redacts credential urls echoed by the backend`() {
        val backendMessage =
            "Request failed for https://api.put.io/v2/files/list" +
                "?oauth_token=backend-secret&cursor=next-page."
        val backendResponseBody = """{"message":"$backendMessage"}"""
        val error =
            PutioApiException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                resolvedStatusCode = 400,
                resolvedErrorType = "INVALID_TOKEN",
                envelope = PutioApiErrorEnvelope(message = backendMessage, statusCode = 400),
                responseBody = backendResponseBody,
                message = backendMessage,
            )

        val localized = PutioErrorLocalizer.localize(error)

        assertEquals(
            "Request failed for https://api.put.io/v2/files/list" +
                "?oauth_token=REDACTED&cursor=next-page.",
            error.message,
        )
        assertEquals(error.message, error.envelope.message)
        assertEquals(
            """{"message":"Request failed for https://api.put.io/v2/files/list""" +
                """?oauth_token=REDACTED&cursor=next-page."}""",
            error.responseBody,
        )
        assertEquals(error.message, localized.failureReason)
        assertFalse(localized.failureReason.contains("backend-secret"))
        assertFalse(error.responseBody.contains("backend-secret"))
    }

    @Test
    fun `api exception redacts credential urls in all envelope text`() {
        val backendUrl = "https://api.put.io/v2/files/list?oauth_token=envelope-secret&cursor=next-page"
        val error =
            PutioApiException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                resolvedStatusCode = 400,
                resolvedErrorType = null,
                envelope =
                    PutioApiErrorEnvelope(
                        message = backendUrl,
                        status = backendUrl,
                        errorType = backendUrl,
                        details =
                            buildJsonObject {
                                put("redirect", JsonPrimitive(backendUrl))
                                put("nested", JsonArray(listOf(JsonPrimitive(backendUrl))))
                            },
                    ),
                responseBody = "{}",
                message = backendUrl,
            )

        val envelopeText = error.envelope.toString()
        assertTrue(envelopeText.contains("oauth_token=REDACTED"))
        assertTrue(envelopeText.contains("cursor=next-page"))
        assertFalse(envelopeText.contains("envelope-secret"))
        assertFalse(error.errorType.orEmpty().contains("envelope-secret"))
    }

    @Test
    fun `serialization exception redacts credential urls stored in response bodies`() {
        val responseBody =
            """{"next":"https:\/\/api.put.io\/v2\/files\/list""" +
                """?oauth_token=body-secret&cursor=next-page"}"""
        val error =
            PutioSerializationException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                responseBody = responseBody,
                cause = IllegalArgumentException("Unexpected JSON input: $responseBody"),
            )

        assertTrue(error.responseBody.contains("""https:\/\/api.put.io"""))
        assertTrue(error.responseBody.contains("oauth_token=REDACTED"))
        assertTrue(error.responseBody.contains("cursor=next-page"))
        assertFalse(error.responseBody.contains("body-secret"))
        val cause = assertIs<SerializationException>(error.cause)
        assertTrue(cause.message.orEmpty().contains("oauth_token=REDACTED"))
        assertTrue(cause.message.orEmpty().contains(IllegalArgumentException::class.java.name))
        assertFalse(cause.message.orEmpty().contains("body-secret"))
    }

    @Test
    fun `message redaction accepts uppercase http schemes`() {
        val redacted =
            redactSensitiveUrlsInText(
                "Retry HTTPS://api.put.io/v2/files/list?oauth_token=uppercase-secret&cursor=next-page",
            )

        assertTrue(redacted.contains("oauth_token=REDACTED"))
        assertTrue(redacted.contains("cursor=next-page"))
        assertFalse(redacted.contains("uppercase-secret"))
    }

    @Test
    fun `message redaction accepts uppercase unicode escaped urls`() {
        val redacted =
            redactSensitiveUrlsInText(
                """Retry HTTPS:\u002F\u002Fapi.put.io\u002Fv2\u002Ffiles\u002Flist""" +
                    """\u003Foauth\u005Ftoken\u003Descaped-secret""" +
                    """\u0026cursor\u003Dnext-page""",
            )

        assertTrue(redacted.contains("""oauth\u005Ftoken\u003DREDACTED"""))
        assertTrue(redacted.contains("""cursor\u003Dnext-page"""))
        assertFalse(redacted.contains("escaped-secret"))
    }

    @Test
    fun `transport exception redacts credential urls in its cause`() {
        val nestedCause =
            UnknownHostException("DNS failed for https://[broken]?oauth_token=nested-secret").apply {
                addSuppressed(
                    SSLHandshakeException(
                        "TLS failed for https://[broken]?oauth_token=suppressed-secret",
                    ),
                )
            }
        val error =
            PutioTransportException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                cause =
                    SocketTimeoutException(
                        "Failed https://[broken]?oauth_token=transport-secret&cursor=next-page",
                    ).apply {
                        initCause(nestedCause)
                    },
            )

        assertEquals(PutioTransportFailureKind.TIMEOUT, error.failureKind)
        val cause = assertIs<SocketTimeoutException>(error.cause)
        assertTrue(cause.message.orEmpty().contains("oauth_token=REDACTED"))
        assertTrue(cause.message.orEmpty().contains("cursor=next-page"))
        assertFalse(cause.message.orEmpty().contains("transport-secret"))
        val redactedNestedCause = assertIs<UnknownHostException>(cause.cause)
        assertFalse(redactedNestedCause.message.orEmpty().contains("nested-secret"))
        val redactedSuppressed = assertIs<SSLHandshakeException>(redactedNestedCause.suppressed.single())
        assertFalse(redactedSuppressed.message.orEmpty().contains("suppressed-secret"))
    }

    @Test
    fun `transport exception classifies standard failure kinds`() {
        val cases =
            listOf(
                SocketTimeoutException("timeout") to PutioTransportFailureKind.TIMEOUT,
                InterruptedIOException("interrupted") to PutioTransportFailureKind.INTERRUPTED,
                UnknownHostException("dns") to PutioTransportFailureKind.DNS,
                ConnectException("connect") to PutioTransportFailureKind.CONNECTION,
                NoRouteToHostException("route") to PutioTransportFailureKind.CONNECTION,
                SocketException("socket") to PutioTransportFailureKind.CONNECTION,
                SSLHandshakeException("tls") to PutioTransportFailureKind.TLS,
                ProtocolException("protocol") to PutioTransportFailureKind.PROTOCOL,
                IOException("io") to PutioTransportFailureKind.IO,
                IllegalStateException("unexpected") to PutioTransportFailureKind.UNEXPECTED,
            )

        cases.forEach { (cause, expectedKind) ->
            val error =
                PutioTransportException(
                    request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                    cause = cause,
                )

            assertEquals(expectedKind, error.failureKind)
        }
    }
}
