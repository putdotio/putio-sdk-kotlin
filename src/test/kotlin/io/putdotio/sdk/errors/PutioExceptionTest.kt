package io.putdotio.sdk.errors

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import java.io.IOException
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
    fun `query redaction leaves non-sensitive and unparsable urls unchanged`() {
        val safeUrl = "https://api.put.io/v2/files/list?cursor=next-page&subtitle_key=all"

        assertEquals(safeUrl, redactSensitiveQueryValues(safeUrl))
        assertEquals("not a url", redactSensitiveQueryValues("not a url"))
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
}
