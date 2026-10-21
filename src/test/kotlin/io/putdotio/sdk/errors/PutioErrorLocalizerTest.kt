package io.putdotio.sdk.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PutioErrorLocalizerTest {
    private data class Trigger(
        val domain: String,
        val operation: String,
        val statusCode: Int,
        val errorType: String,
    )

    // `names` are the failure each error code or route names, so a message swapped onto the wrong code fails.
    private class Guidance(
        val names: List<String>,
        val triggers: List<Trigger>,
    )

    // Statuses mirror each operation's PutioOperationErrorSpec. Triggers in one guidance share copy; guidances never share a message.
    private val operationGuidance =
        listOf(
            Guidance(listOf("device code"), listOf(Trigger("auth", "checkCodeMatch", 404, "NOT_FOUND"))),
            Guidance(
                listOf("access token"),
                listOf(Trigger("auth", "validateToken", 401, "INVALID_TOKEN"), Trigger("auth", "validateToken", 403, "FORBIDDEN")),
            ),
            Guidance(listOf("two-factor", "already exists"), listOf(Trigger("auth", "generateTotp", 403, "already_exists"))),
            Guidance(
                listOf("two-factor", "code"),
                listOf(Trigger("auth", "verifyTotp", 400, "invalid_code"), Trigger("auth", "verifyTotp", 400, "code_not_found")),
            ),
            Guidance(
                listOf("two-factor", "not enabled"),
                listOf("verifyTotp", "getRecoveryCodes", "regenerateRecoveryCodes").map { Trigger("auth", it, 400, "invalid_setup") },
            ),
            Guidance(
                listOf("two-factor", "token"),
                listOf("generateTotp", "verifyTotp", "getRecoveryCodes", "regenerateRecoveryCodes").map {
                    Trigger("auth", it, 401, "invalid_scope")
                },
            ),
            Guidance(listOf("url", "required"), listOf(Trigger("transfers", "add", 400, "EMPTY_URL"))),
            Guidance(listOf("too many", "urls"), listOf(Trigger("transfers", "addMany", 403, "TOO_MANY_URLS"))),
            Guidance(
                listOf("transfer", "not be found"),
                listOf(Trigger("transfers", "get", 404, "NOT_FOUND"), Trigger("transfers", "retry", 404, "NOT_FOUND")),
            ),
            Guidance(listOf("file", "not be found"), listOf(Trigger("files", "get", 404, "NOT_FOUND"))),
            Guidance(
                listOf("search query", "too long"),
                listOf("search", "continueSearch").map { Trigger("files", it, 400, "SEARCH_TOO_LONG_QUERY") },
            ),
            Guidance(listOf("folder name", "required"), listOf(Trigger("files", "createFolder", 400, "EMPTY_NAME"))),
            Guidance(listOf("slash"), listOf(Trigger("files", "createFolder", 400, "SLASH_IN_NAME"))),
            Guidance(listOf("folder name", "too long"), listOf(Trigger("files", "createFolder", 400, "NAME_TOO_LONG"))),
            Guidance(listOf("folder", "already exists"), listOf(Trigger("files", "createFolder", 400, "NAME_ALREADY_EXIST"))),
            Guidance(listOf("resume", "not available"), listOf(Trigger("files", "startFrom", 400, "FEATURE_DISABLED"))),
            Guidance(listOf("media"), listOf(Trigger("files", "startFrom", 400, "INVALID_MEDIA"))),
            Guidance(listOf("subtitles"), listOf(Trigger("files", "listSubtitles", 404, "NOT_FOUND"))),
            Guidance(listOf("trash item", "not be found"), listOf(Trigger("trash", "restore", 404, "NOT_FOUND"))),
            Guidance(listOf("trash item", "no longer available"), listOf(Trigger("trash", "delete", 404, "NOT_FOUND"))),
            Guidance(listOf("page size"), listOf(Trigger("events", "list", 400, "INVALID_PER_PAGE"))),
        )

    @Test
    fun `every mapped operation error gets its own complete guidance instead of the generic api copy`() {
        val messages =
            operationGuidance.map { guidance ->
                val localized = guidance.triggers.map { trigger -> trigger to PutioErrorLocalizer.localize(trigger.operationError()) }
                localized.forEach { (trigger, error) ->
                    guidance.names.forEach { name ->
                        assertTrue(error.message.contains(name, ignoreCase = true), "$trigger message names '$name': ${error.message}")
                    }
                    assertTrue(error.message.isNotBlank(), "$trigger message")
                    assertTrue(error.recoverySuggestion.description.isNotBlank(), "$trigger recovery")
                    assertIs<PutioRecoverySuggestion.Instruction>(error.recoverySuggestion, "$trigger recovery")
                    assertEquals(trigger.backendMessage, error.failureReason, "$trigger failure reason")
                    assertNotEquals(PutioErrorLocalizer.localize(trigger.apiError()).message, error.message, "$trigger is unmapped")
                    assertEquals(trigger.operationMeta(), error.meta, "$trigger meta")
                }
                val (first, firstError) = localized.first()
                localized.drop(1).forEach { (trigger, error) ->
                    assertEquals(firstError.message, error.message, "$trigger shares guidance with $first")
                    assertEquals(firstError.recoverySuggestion, error.recoverySuggestion, "$trigger shares guidance with $first")
                }
                firstError.message
            }

        assertEquals(messages.size, messages.toSet().size, "guidance messages must be distinct: $messages")
    }

    @Test
    fun `unmapped error codes on a mapped operation fall back to the underlying api guidance`() {
        val unmapped = operationGuidance.flatMap { it.triggers }.map { it.copy(statusCode = 400, errorType = "UNMAPPED_CODE") }.distinct()
        unmapped.forEach { trigger ->
            val fallback = PutioErrorLocalizer.localize(trigger.apiError())
            val localized = PutioErrorLocalizer.localize(trigger.operationError())

            assertEquals(fallback.message, localized.message, "$trigger")
            assertEquals(fallback.recoverySuggestion, localized.recoverySuggestion, "$trigger")
            assertEquals(fallback.meta + trigger.operationMeta(), localized.meta, "$trigger")
            assertIs<PutioOperationException>(localized.underlyingError)
        }
    }

    @Test
    fun `api errors are classified by status before error type`() {
        fun localize(
            statusCode: Int,
            errorType: String?,
        ) = PutioErrorLocalizer.localize(apiError(statusCode = statusCode, errorType = errorType))

        val unauthorized = localize(401, "INVALID_TOKEN")
        val forbidden = localize(403, "CAPTCHA_REQUIRED")
        val notFound = localize(404, "CAPTCHA_REQUIRED")
        val rateLimited = localize(429, null)
        val captcha = localize(400, "CAPTCHA_REQUIRED")
        val generic = localize(400, "WEIRD_BACKEND_ERROR")

        assertEquals(unauthorized.message, forbidden.message)
        assertEquals(generic.message, localize(500, "OTHER_BACKEND_ERROR").message)
        val classes = listOf(unauthorized, notFound, rateLimited, captcha, generic)
        assertEquals(classes.size, classes.map { it.message }.toSet().size)
        assertIs<PutioRecoverySuggestion.Captcha>(captcha.recoverySuggestion)
        (classes - captcha).forEach { assertIs<PutioRecoverySuggestion.Instruction>(it.recoverySuggestion) }
        classes.forEach { localized ->
            assertTrue(localized.message.isNotBlank())
            assertTrue(localized.recoverySuggestion.description.isNotBlank())
            assertEquals("backend said no", localized.failureReason)
        }
        assertEquals(
            mapOf("method" to "GET", "url" to "https://api.put.io/v2/files/99", "statusCode" to "404", "errorType" to "CAPTCHA_REQUIRED"),
            notFound.meta,
        )
        assertEquals(setOf("method", "url", "statusCode"), rateLimited.meta.keys)
    }

    @Test
    fun `sdk-side failures get distinct guidance that survives operation wrapping`() {
        val request = PutioRequestData(method = "GET", url = "https://api.put.io/v2/files/list")
        val failures =
            listOf(
                PutioConfigurationException("Missing access token"),
                PutioTransportException(request = request, cause = IllegalStateException("offline")),
                PutioSerializationException(
                    request = request,
                    responseBody = """{"status":"OK"}""",
                    cause = IllegalArgumentException("bad json"),
                ),
            )

        val localized = failures.map(PutioErrorLocalizer::localize)

        val messages = localized.map { it.message } + PutioErrorLocalizer.localize(apiError(400, "X")).message
        assertEquals(messages.size, messages.toSet().size, "sdk-side and api guidance must be distinct: $messages")
        assertEquals("Missing access token", localized[0].failureReason)
        localized.drop(1).forEach { assertEquals(mapOf("method" to "GET", "url" to request.url), it.meta) }
        failures.zip(localized).forEach { (failure, direct) ->
            assertTrue(direct.message.isNotBlank())
            assertTrue(direct.recoverySuggestion.description.isNotBlank())
            assertIs<PutioRecoverySuggestion.Instruction>(direct.recoverySuggestion)

            val operation = PutioOperationException("files", "list", contract = null, reason = null, underlyingError = failure)
            val wrapped = PutioErrorLocalizer.localize(operation)
            assertEquals(
                direct.copy(underlyingError = operation, meta = direct.meta + mapOf("domain" to "files", "operation" to "list")),
                wrapped,
            )
        }
    }

    @Test
    fun `unexpected errors surface their own message`() {
        val localized = PutioErrorLocalizer.localize(IllegalStateException("boom"))

        assertEquals("boom", localized.message)
        assertEquals("boom", localized.failureReason)
        assertTrue(localized.recoverySuggestion.description.isNotBlank())
    }

    private val Trigger.backendMessage: String
        get() = "backend rejected $domain.$operation with $errorType"

    private fun Trigger.apiError() =
        apiError(
            statusCode = statusCode,
            errorType = errorType,
            url = "https://api.put.io/v2/$domain/$operation",
            message = backendMessage,
        )

    private fun Trigger.operationError() =
        PutioOperationException(
            domain = domain,
            operation = operation,
            contract = PutioKnownErrorContract(errorType = errorType, statusCode = statusCode),
            reason = PutioOperationErrorReason.ErrorType(errorType),
            underlyingError = apiError(),
        )

    private fun Trigger.operationMeta() =
        mapOf(
            "domain" to domain,
            "operation" to operation,
            "contractErrorType" to errorType,
            "contractStatusCode" to statusCode.toString(),
        )

    private fun apiError(
        statusCode: Int,
        errorType: String?,
        url: String = "https://api.put.io/v2/files/99",
        message: String = "backend said no",
    ) = PutioApiException(
        request = PutioRequestData(method = "GET", url = url),
        resolvedStatusCode = statusCode,
        resolvedErrorType = errorType,
        envelope = PutioApiErrorEnvelope(message = message, statusCode = statusCode, errorType = errorType),
        responseBody = "{}",
        message = message,
    )
}
