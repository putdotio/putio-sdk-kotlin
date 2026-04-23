package io.putdotio.sdk.errors

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PutioExceptionTest {
    @Test
    fun `api exception falls back to resolved status and error type when envelope fields are absent`() {
        val error = PutioApiException(
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
        val error = assertFailsWith<PutioOperationException> {
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
                        envelope = PutioApiErrorEnvelope(
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
        val existing = PutioOperationException(
            domain = "auth",
            operation = "validateToken",
            contract = PutioKnownErrorContract(statusCode = 401),
            reason = PutioOperationErrorReason.StatusCode(401),
            underlyingError = PutioConfigurationException("Missing access token"),
        )

        val error = assertFailsWith<PutioOperationException> {
            runBlocking {
                putioOperation(PutioOperationErrorSpec(domain = "auth", operation = "validateToken")) {
                    throw existing
                }
            }
        }

        assertEquals(existing, error)
    }
}
