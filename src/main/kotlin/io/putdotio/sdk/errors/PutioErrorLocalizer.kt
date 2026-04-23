package io.putdotio.sdk.errors

sealed interface PutioRecoverySuggestion {
    val description: String

    data class Instruction(
        override val description: String,
    ) : PutioRecoverySuggestion

    data class Captcha(
        override val description: String,
    ) : PutioRecoverySuggestion
}

data class PutioLocalizedError(
    val message: String,
    val failureReason: String,
    val recoverySuggestion: PutioRecoverySuggestion,
    val underlyingError: Throwable,
    val meta: Map<String, String> = emptyMap(),
)

object PutioErrorLocalizer {
    fun localize(error: Throwable): PutioLocalizedError =
        when (error) {
            is PutioOperationException -> localizeOperation(error)
            is PutioConfigurationException ->
                localizeConfiguration(error)
            is PutioTransportException ->
                localizeTransport(error)
            is PutioSerializationException ->
                localizeSerialization(error)
            is PutioApiException -> localizeApi(error)
            else -> localizeUnexpected(error)
        }

    private fun localizeConfiguration(error: PutioConfigurationException): PutioLocalizedError =
        PutioLocalizedError(
            message = "The SDK is missing required configuration",
            failureReason = error.message ?: "A required put.io SDK setting is missing.",
            recoverySuggestion = PutioRecoverySuggestion.Instruction(
                "Provide the missing token or client configuration, then retry the request.",
            ),
            underlyingError = error,
        )

    private fun localizeTransport(error: PutioTransportException): PutioLocalizedError =
        PutioLocalizedError(
            message = "The SDK could not reach put.io",
            failureReason = "Transport failure for ${error.request.method} ${error.request.url}.",
            recoverySuggestion = PutioRecoverySuggestion.Instruction(
                "Check connectivity and retry the request.",
            ),
            underlyingError = error,
            meta = mapOf(
                "method" to error.request.method,
                "url" to error.request.url,
            ),
        )

    private fun localizeSerialization(error: PutioSerializationException): PutioLocalizedError =
        PutioLocalizedError(
            message = "put.io returned data the SDK could not parse",
            failureReason = "Response parsing failed for ${error.request.method} ${error.request.url}.",
            recoverySuggestion = PutioRecoverySuggestion.Instruction(
                "Upgrade the SDK or inspect the raw response body to confirm whether the backend contract changed.",
            ),
            underlyingError = error,
            meta = mapOf(
                "method" to error.request.method,
                "url" to error.request.url,
            ),
        )

    private fun localizeUnexpected(error: Throwable): PutioLocalizedError =
        PutioLocalizedError(
            message = error.message ?: "The SDK failed unexpectedly",
            failureReason = error.localizedMessage ?: "An unclassified SDK error occurred.",
            recoverySuggestion = PutioRecoverySuggestion.Instruction(
                "Retry the operation and inspect the underlying exception if it keeps failing.",
            ),
            underlyingError = error,
        )

    private fun localizeOperation(error: PutioOperationException): PutioLocalizedError {
        val meta = buildMap {
            put("domain", error.domain)
            put("operation", error.operation)
            error.contract?.errorType?.let { put("contractErrorType", it) }
            error.contract?.statusCode?.let { put("contractStatusCode", it.toString()) }
        }

        specificOperationLocalizer(error)?.let { localized ->
            return localized.copy(meta = localized.meta + meta)
        }

        val base =
            when (val underlying = error.underlyingError) {
                is PutioConfigurationException -> localizeConfiguration(underlying)
                is PutioTransportException -> localizeTransport(underlying)
                is PutioSerializationException -> localizeSerialization(underlying)
                is PutioApiException -> localizeApi(underlying)
                else -> localizeUnexpected(underlying)
            }

        return base.copy(
            underlyingError = error,
            meta = base.meta + meta,
        )
    }

    private fun localizeApi(error: PutioApiException): PutioLocalizedError {
        val apiMessage = error.envelope.message ?: error.message ?: "put.io returned HTTP ${error.statusCode}"
        val meta = buildMap {
            put("method", error.request.method)
            put("url", error.request.url)
            put("statusCode", error.statusCode.toString())
            error.errorType?.let { put("errorType", it) }
        }

        return when {
            error.statusCode == 401 || error.statusCode == 403 ->
                PutioLocalizedError(
                    message = "Authentication failed",
                    failureReason = apiMessage,
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Sign in again or refresh the access token, then retry.",
                    ),
                    underlyingError = error,
                    meta = meta,
                )
            error.statusCode == 404 ->
                PutioLocalizedError(
                    message = "The requested put.io resource could not be found",
                    failureReason = apiMessage,
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Verify the identifier or path, then retry.",
                    ),
                    underlyingError = error,
                    meta = meta,
                )
            error.statusCode == 429 ->
                PutioLocalizedError(
                    message = "put.io is rate-limiting this request",
                    failureReason = apiMessage,
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Wait briefly before retrying.",
                    ),
                    underlyingError = error,
                    meta = meta,
                )
            error.errorType == "CAPTCHA_REQUIRED" ->
                PutioLocalizedError(
                    message = "put.io needs an additional verification step",
                    failureReason = apiMessage,
                    recoverySuggestion = PutioRecoverySuggestion.Captcha(
                        "Complete the required CAPTCHA or verification challenge, then retry.",
                    ),
                    underlyingError = error,
                    meta = meta,
                )
            else ->
                PutioLocalizedError(
                    message = "put.io rejected the request",
                    failureReason = apiMessage,
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Retry the request. If it keeps failing, inspect the error type and status code.",
                    ),
                    underlyingError = error,
                    meta = meta,
                )
        }
    }

    private fun specificOperationLocalizer(error: PutioOperationException): PutioLocalizedError? =
        when {
            error.matches(domain = "auth", operation = "checkCodeMatch", statusCode = 404) ->
                PutioLocalizedError(
                    message = "The device code is invalid or expired",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Request a fresh device code and retry the pairing flow.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "auth", operation = "validateToken", statusCode = 401) ||
                error.matches(domain = "auth", operation = "validateToken", statusCode = 403) ->
                PutioLocalizedError(
                    message = "The access token is invalid or expired",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Refresh the token or sign in again, then retry.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "auth", operation = "generateTotp", errorType = "already_exists") ->
                PutioLocalizedError(
                    message = "Two-factor setup already exists",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Use the existing two-factor setup or disable it before starting setup again.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "auth", operation = "verifyTotp", errorType = "invalid_code") ||
                error.matches(domain = "auth", operation = "verifyTotp", errorType = "code_not_found") ->
                PutioLocalizedError(
                    message = "The two-factor code is invalid",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Ask for a current TOTP code or unused recovery code, then retry verification.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "auth", operation = "verifyTotp", errorType = "invalid_setup") ||
                error.matches(domain = "auth", operation = "getRecoveryCodes", errorType = "invalid_setup") ||
                error.matches(domain = "auth", operation = "regenerateRecoveryCodes", errorType = "invalid_setup") ->
                PutioLocalizedError(
                    message = "Two-factor authentication is not enabled",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Confirm that the account has two-factor authentication enabled before retrying.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "auth", operation = "generateTotp", errorType = "invalid_scope") ||
                error.matches(domain = "auth", operation = "verifyTotp", errorType = "invalid_scope") ||
                error.matches(domain = "auth", operation = "getRecoveryCodes", errorType = "invalid_scope") ||
                error.matches(domain = "auth", operation = "regenerateRecoveryCodes", errorType = "invalid_scope") ->
                PutioLocalizedError(
                    message = "The token cannot access this two-factor flow",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Use a token with the required two-factor or restricted scope, then retry.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "get", statusCode = 404) ->
                PutioLocalizedError(
                    message = "The requested file could not be found",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Verify the file identifier or reload the parent listing, then retry.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "search", errorType = "SEARCH_TOO_LONG_QUERY") ->
                PutioLocalizedError(
                    message = "The search query is too long",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Shorten the query text and retry the search.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "createFolder", errorType = "EMPTY_NAME") ->
                PutioLocalizedError(
                    message = "Folder name is required",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Provide a non-empty folder name and retry.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "createFolder", errorType = "SLASH_IN_NAME") ->
                PutioLocalizedError(
                    message = "Folder names cannot contain slashes",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Remove slash characters from the folder name and retry.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "createFolder", errorType = "NAME_TOO_LONG") ->
                PutioLocalizedError(
                    message = "Folder name is too long",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Shorten the folder name and retry.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "createFolder", errorType = "NAME_ALREADY_EXIST") ->
                PutioLocalizedError(
                    message = "A folder with this name already exists",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Choose a different folder name or reuse the existing folder.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "startFrom", errorType = "FEATURE_DISABLED") ->
                PutioLocalizedError(
                    message = "Resume playback is not available for this file",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Try a different media file or confirm that resume playback is enabled for this account.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "startFrom", errorType = "INVALID_MEDIA") ->
                PutioLocalizedError(
                    message = "Start position is only available for supported media files",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Retry the request with a playable audio or video file.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "files", operation = "listSubtitles", statusCode = 404) ->
                PutioLocalizedError(
                    message = "Subtitles are not available for this file",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Check that the file exists and supports subtitles, then retry.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "trash", operation = "restore", statusCode = 404) ->
                PutioLocalizedError(
                    message = "The trash item could not be found",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Reload the trash listing and retry the restore action.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "trash", operation = "delete", statusCode = 404) ->
                PutioLocalizedError(
                    message = "The trash item is no longer available",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Refresh the trash listing and retry only if the item is still present.",
                    ),
                    underlyingError = error,
                )
            error.matches(domain = "events", operation = "list", errorType = "INVALID_PER_PAGE") ->
                PutioLocalizedError(
                    message = "The history page size is invalid",
                    failureReason = apiMessage(error),
                    recoverySuggestion = PutioRecoverySuggestion.Instruction(
                        "Use a positive per-page value supported by the API and retry.",
                    ),
                    underlyingError = error,
                )
            else -> null
        }

    private fun PutioOperationException.matches(
        domain: String,
        operation: String,
        errorType: String? = null,
        statusCode: Int? = null,
    ): Boolean {
        if (this.domain != domain || this.operation != operation) {
            return false
        }

        val apiError = underlyingError as? PutioApiException ?: return false
        if (errorType != null && apiError.errorType != errorType) {
            return false
        }

        if (statusCode != null && apiError.statusCode != statusCode) {
            return false
        }

        return true
    }

    private fun apiMessage(error: PutioOperationException): String {
        val apiError = error.underlyingError as? PutioApiException
        return apiError?.envelope?.message ?: apiError?.message ?: "put.io ${error.domain}.${error.operation} failed"
    }
}
