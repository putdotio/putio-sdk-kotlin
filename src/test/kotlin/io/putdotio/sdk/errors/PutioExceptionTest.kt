package io.putdotio.sdk.errors

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

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
}
