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
    fun `localizer covers direct api auth and generic fallback guidance`() {
        val auth = PutioErrorLocalizer.localize(
            PutioApiException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/oauth2/validate"),
                resolvedStatusCode = 401,
                resolvedErrorType = "INVALID_TOKEN",
                envelope = PutioApiErrorEnvelope(
                    message = "invalid token",
                    statusCode = 401,
                    errorType = "INVALID_TOKEN",
                ),
                responseBody = """{"error_type":"INVALID_TOKEN"}""",
                message = "invalid token",
            ),
        )
        assertEquals("Authentication failed", auth.message)
        assertEquals("401", auth.meta["statusCode"])

        val generic = PutioErrorLocalizer.localize(
            PutioApiException(
                request = PutioRequestData(method = "POST", url = "https://api.put.io/v2/files/create-folder"),
                resolvedStatusCode = 400,
                resolvedErrorType = "WEIRD_BACKEND_ERROR",
                envelope = PutioApiErrorEnvelope(
                    message = "backend said no",
                    statusCode = 400,
                    errorType = "WEIRD_BACKEND_ERROR",
                ),
                responseBody = """{"error_type":"WEIRD_BACKEND_ERROR"}""",
                message = "backend said no",
            ),
        )
        assertEquals("put.io rejected the request", generic.message)
        assertEquals("WEIRD_BACKEND_ERROR", generic.meta["errorType"])
    }

    @Test
    fun `localizer gives operation-specific guidance for long file searches`() {
        val localized = PutioErrorLocalizer.localize(
            operationError(
                domain = "files",
                operation = "search",
                statusCode = 400,
                errorType = "SEARCH_TOO_LONG_QUERY",
                message = "search query too long",
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
            operationError(
                domain = "events",
                operation = "list",
                statusCode = 400,
                errorType = "INVALID_PER_PAGE",
                message = "per_page must be positive",
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

    @Test
    fun `localizer covers auth operation guidance`() {
        val invalidCode = PutioErrorLocalizer.localize(
            operationError(
                domain = "auth",
                operation = "checkCodeMatch",
                statusCode = 404,
                errorType = "NOT_FOUND",
                message = "device code not found",
            ),
        )
        assertEquals("The device code is invalid or expired", invalidCode.message)
        assertEquals("checkCodeMatch", invalidCode.meta["operation"])

        val invalidToken = PutioErrorLocalizer.localize(
            operationError(
                domain = "auth",
                operation = "validateToken",
                statusCode = 401,
                errorType = "INVALID_TOKEN",
                message = "invalid token",
            ),
        )
        assertEquals("The access token is invalid or expired", invalidToken.message)
        assertEquals("validateToken", invalidToken.meta["operation"])
    }

    @Test
    fun `localizer covers two-factor auth guidance`() {
        val alreadyExists = PutioErrorLocalizer.localize(
            operationError(
                domain = "auth",
                operation = "generateTotp",
                statusCode = 403,
                errorType = "already_exists",
                message = "already configured",
            ),
        )
        assertEquals("Two-factor setup already exists", alreadyExists.message)

        val invalidCode = PutioErrorLocalizer.localize(
            operationError(
                domain = "auth",
                operation = "verifyTotp",
                statusCode = 400,
                errorType = "code_not_found",
                message = "invalid totp code",
            ),
        )
        assertEquals("The two-factor code is invalid", invalidCode.message)
        assertEquals("verifyTotp", invalidCode.meta["operation"])

        val invalidSetup = PutioErrorLocalizer.localize(
            operationError(
                domain = "auth",
                operation = "getRecoveryCodes",
                statusCode = 400,
                errorType = "invalid_setup",
                message = "two-factor is not enabled",
            ),
        )
        assertEquals("Two-factor authentication is not enabled", invalidSetup.message)

        val invalidScope = PutioErrorLocalizer.localize(
            operationError(
                domain = "auth",
                operation = "regenerateRecoveryCodes",
                statusCode = 401,
                errorType = "invalid_scope",
                message = "invalid scope",
            ),
        )
        assertEquals("The token cannot access this two-factor flow", invalidScope.message)
    }

    @Test
    fun `localizer covers folder creation guidance for common validation failures`() {
        val cases = listOf(
            Triple("EMPTY_NAME", "Folder name is required", "empty folder name"),
            Triple("SLASH_IN_NAME", "Folder names cannot contain slashes", "slash not allowed"),
            Triple("NAME_TOO_LONG", "Folder name is too long", "name too long"),
            Triple("NAME_ALREADY_EXIST", "A folder with this name already exists", "already exists"),
        )

        for ((errorType, expectedMessage, message) in cases) {
            val localized = PutioErrorLocalizer.localize(
                operationError(
                    domain = "files",
                    operation = "createFolder",
                    statusCode = 400,
                    errorType = errorType,
                    message = message,
                ),
            )

            assertEquals(expectedMessage, localized.message)
            assertEquals(errorType, localized.meta["contractErrorType"])
        }
    }

    @Test
    fun `localizer covers start-from and subtitle guidance`() {
        val missingFile = PutioErrorLocalizer.localize(
            operationError(
                domain = "files",
                operation = "get",
                statusCode = 404,
                errorType = "NOT_FOUND",
                message = "missing file",
            ),
        )
        assertEquals("The requested file could not be found", missingFile.message)

        val featureDisabled = PutioErrorLocalizer.localize(
            operationError(
                domain = "files",
                operation = "startFrom",
                statusCode = 400,
                errorType = "FEATURE_DISABLED",
                message = "resume is disabled",
            ),
        )
        assertEquals("Resume playback is not available for this file", featureDisabled.message)

        val invalidMedia = PutioErrorLocalizer.localize(
            operationError(
                domain = "files",
                operation = "startFrom",
                statusCode = 400,
                errorType = "INVALID_MEDIA",
                message = "not a playable file",
            ),
        )
        assertEquals("Start position is only available for supported media files", invalidMedia.message)

        val subtitles = PutioErrorLocalizer.localize(
            operationError(
                domain = "files",
                operation = "listSubtitles",
                statusCode = 404,
                errorType = "NOT_FOUND",
                message = "subtitles missing",
            ),
        )
        assertEquals("Subtitles are not available for this file", subtitles.message)
    }

    @Test
    fun `localizer covers trash delete guidance`() {
        val restore = PutioErrorLocalizer.localize(
            operationError(
                domain = "trash",
                operation = "restore",
                statusCode = 404,
                errorType = "NOT_FOUND",
                message = "trash item missing",
            ),
        )
        assertEquals("The trash item could not be found", restore.message)

        val localized = PutioErrorLocalizer.localize(
            operationError(
                domain = "trash",
                operation = "delete",
                statusCode = 404,
                errorType = "NOT_FOUND",
                message = "trash item missing",
            ),
        )

        assertEquals("The trash item is no longer available", localized.message)
        assertEquals("trash", localized.meta["domain"])
        assertEquals("delete", localized.meta["operation"])
    }

    @Test
    fun `localizer preserves operation metadata for transport and configuration fallbacks`() {
        val transport = PutioErrorLocalizer.localize(
            PutioOperationException(
                domain = "files",
                operation = "list",
                contract = null,
                reason = null,
                underlyingError = PutioTransportException(
                    request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                    cause = IllegalStateException("offline"),
                ),
            ),
        )
        assertEquals("The SDK could not reach put.io", transport.message)
        assertEquals("files", transport.meta["domain"])
        assertEquals("list", transport.meta["operation"])
        assertEquals("GET", transport.meta["method"])

        val configuration = PutioErrorLocalizer.localize(
            PutioOperationException(
                domain = "auth",
                operation = "validateToken",
                contract = null,
                reason = null,
                underlyingError = PutioConfigurationException("Missing access token"),
            ),
        )
        assertEquals("The SDK is missing required configuration", configuration.message)
        assertEquals("auth", configuration.meta["domain"])
        assertEquals("validateToken", configuration.meta["operation"])

        val serialization = PutioErrorLocalizer.localize(
            PutioOperationException(
                domain = "files",
                operation = "list",
                contract = null,
                reason = null,
                underlyingError = PutioSerializationException(
                    request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                    responseBody = """{"status":"OK"}""",
                    cause = IllegalArgumentException("bad json"),
                ),
            ),
        )
        assertEquals("put.io returned data the SDK could not parse", serialization.message)
        assertEquals("files", serialization.meta["domain"])
        assertEquals("list", serialization.meta["operation"])
    }

    @Test
    fun `localizer classifies unexpected errors`() {
        val localized = PutioErrorLocalizer.localize(IllegalStateException("boom"))

        assertEquals("boom", localized.message)
        assertEquals("boom", localized.failureReason)
        assertIs<PutioRecoverySuggestion.Instruction>(localized.recoverySuggestion)
    }

    @Test
    fun `localizer handles transport serialization captcha and rate limit cases`() {
        val transport = PutioErrorLocalizer.localize(
            PutioTransportException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/9"),
                cause = IllegalStateException("offline"),
            ),
        )
        assertEquals("The SDK could not reach put.io", transport.message)
        assertEquals("GET", transport.meta["method"])

        val serialization = PutioErrorLocalizer.localize(
            PutioSerializationException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/9"),
                responseBody = """{"status":"OK"}""",
                cause = IllegalArgumentException("bad json"),
            ),
        )
        assertEquals("put.io returned data the SDK could not parse", serialization.message)
        assertEquals("https://api.put.io/v2/files/9", serialization.meta["url"])

        val captcha = PutioErrorLocalizer.localize(
            PutioApiException(
                request = PutioRequestData(method = "POST", url = "https://api.put.io/v2/auth/login"),
                resolvedStatusCode = 400,
                resolvedErrorType = "CAPTCHA_REQUIRED",
                envelope = PutioApiErrorEnvelope(
                    message = "captcha required",
                    statusCode = 400,
                    errorType = "CAPTCHA_REQUIRED",
                ),
                responseBody = """{"error_type":"CAPTCHA_REQUIRED"}""",
                message = "captcha required",
            ),
        )
        assertEquals("put.io needs an additional verification step", captcha.message)
        assertIs<PutioRecoverySuggestion.Captcha>(captcha.recoverySuggestion)

        val rateLimited = PutioErrorLocalizer.localize(
            PutioApiException(
                request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list"),
                resolvedStatusCode = 429,
                resolvedErrorType = null,
                envelope = PutioApiErrorEnvelope(
                    message = "slow down",
                    statusCode = 429,
                ),
                responseBody = """{"status_code":429}""",
                message = "slow down",
            ),
        )
        assertEquals("put.io is rate-limiting this request", rateLimited.message)
        assertIs<PutioRecoverySuggestion.Instruction>(rateLimited.recoverySuggestion)
    }

    private fun operationError(
        domain: String,
        operation: String,
        statusCode: Int,
        errorType: String,
        message: String,
    ) = PutioOperationException(
        domain = domain,
        operation = operation,
        contract = PutioKnownErrorContract(errorType = errorType, statusCode = statusCode),
        reason = PutioOperationErrorReason.ErrorType(errorType),
        underlyingError = PutioApiException(
            request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/$domain/$operation"),
            resolvedStatusCode = statusCode,
            resolvedErrorType = errorType,
            envelope = PutioApiErrorEnvelope(
                message = message,
                statusCode = statusCode,
                errorType = errorType,
            ),
            responseBody = """{"error_type":"$errorType"}""",
            message = message,
        ),
    )
}
