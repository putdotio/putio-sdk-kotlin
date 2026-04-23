package io.putdotio.sdk.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PutioErrorLocalizerTest {
    @Test
    fun `localizer turns api not found errors into actionable guidance`() {
        val error = PutioApiException(
            request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/99"),
            resolvedStatusCode = 404,
            resolvedErrorType = "NOT_FOUND",
            envelope = PutioApiErrorEnvelope(
                message = "file not found",
                statusCode = 404,
                errorType = "NOT_FOUND",
            ),
            responseBody = """{"error_type":"NOT_FOUND"}""",
            message = "file not found",
        )

        val localized = PutioErrorLocalizer.localize(error)

        assertEquals("The requested put.io resource could not be found", localized.message)
        assertEquals("file not found", localized.failureReason)
        assertEquals("404", localized.meta["statusCode"])
        assertEquals("NOT_FOUND", localized.meta["errorType"])
        assertIs<PutioRecoverySuggestion.Instruction>(localized.recoverySuggestion)
    }

    @Test
    fun `localizer gives operation-specific guidance for long file searches`() {
        val localized = PutioErrorLocalizer.localize(
            PutioOperationException(
                domain = "files",
                operation = "search",
                contract = PutioKnownErrorContract(errorType = "SEARCH_TOO_LONG_QUERY", statusCode = 400),
                reason = PutioOperationErrorReason.ErrorType("SEARCH_TOO_LONG_QUERY"),
                underlyingError = PutioApiException(
                    request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/search"),
                    resolvedStatusCode = 400,
                    resolvedErrorType = "SEARCH_TOO_LONG_QUERY",
                    envelope = PutioApiErrorEnvelope(
                        message = "search query too long",
                        statusCode = 400,
                        errorType = "SEARCH_TOO_LONG_QUERY",
                    ),
                    responseBody = """{"error_type":"SEARCH_TOO_LONG_QUERY"}""",
                    message = "search query too long",
                ),
            ),
        )

        assertEquals("The search query is too long", localized.message)
        assertEquals("search query too long", localized.failureReason)
        assertEquals("files", localized.meta["domain"])
        assertEquals("search", localized.meta["operation"])
        assertEquals("SEARCH_TOO_LONG_QUERY", localized.meta["contractErrorType"])
        assertIs<PutioRecoverySuggestion.Instruction>(localized.recoverySuggestion)
    }

    @Test
    fun `localizer gives operation-specific guidance for invalid history pagination`() {
        val localized = PutioErrorLocalizer.localize(
            PutioOperationException(
                domain = "events",
                operation = "list",
                contract = PutioKnownErrorContract(errorType = "INVALID_PER_PAGE", statusCode = 400),
                reason = PutioOperationErrorReason.ErrorType("INVALID_PER_PAGE"),
                underlyingError = PutioApiException(
                    request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/events/list"),
                    resolvedStatusCode = 400,
                    resolvedErrorType = "INVALID_PER_PAGE",
                    envelope = PutioApiErrorEnvelope(
                        message = "per_page must be positive",
                        statusCode = 400,
                        errorType = "INVALID_PER_PAGE",
                    ),
                    responseBody = """{"error_type":"INVALID_PER_PAGE"}""",
                    message = "per_page must be positive",
                ),
            ),
        )

        assertEquals("The history page size is invalid", localized.message)
        assertEquals("per_page must be positive", localized.failureReason)
        assertEquals("events", localized.meta["domain"])
        assertEquals("list", localized.meta["operation"])
        assertIs<PutioRecoverySuggestion.Instruction>(localized.recoverySuggestion)
    }

    @Test
    fun `localizer turns missing config into a setup hint`() {
        val localized = PutioErrorLocalizer.localize(
            PutioConfigurationException("Missing access token"),
        )

        assertEquals("The SDK is missing required configuration", localized.message)
        assertEquals("Missing access token", localized.failureReason)
        assertIs<PutioRecoverySuggestion.Instruction>(localized.recoverySuggestion)
    }
}
