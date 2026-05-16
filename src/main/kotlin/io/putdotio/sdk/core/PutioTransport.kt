package io.putdotio.sdk.core

import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioApiErrorEnvelope
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioRequestData
import io.putdotio.sdk.errors.PutioSerializationException
import io.putdotio.sdk.errors.PutioTransportException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal sealed interface PutioAuth {
    data object ConfigToken : PutioAuth

    data class Token(val value: String) : PutioAuth

    data object None : PutioAuth
}

internal class PutioTransport(
    internal val config: PutioConfig,
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T = execute(
        method = "GET",
        path = path,
        serializer = serializer,
        query = query,
        auth = auth,
    )

    suspend fun <T> post(
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String> = emptyMap(),
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T = execute(
        method = "POST",
        path = path,
        serializer = serializer,
        query = query,
        form = form,
        auth = auth,
    )

    suspend fun <T, TBody> postJson(
        path: String,
        serializer: KSerializer<T>,
        body: TBody,
        bodySerializer: KSerializer<TBody>,
        query: Map<String, String> = emptyMap(),
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T = execute(
        method = "POST",
        path = path,
        serializer = serializer,
        query = query,
        jsonBody = json.encodeToString(bodySerializer, body),
        auth = auth,
    )

    suspend fun <T, TBody> putJson(
        path: String,
        serializer: KSerializer<T>,
        body: TBody,
        bodySerializer: KSerializer<TBody>,
        query: Map<String, String> = emptyMap(),
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T = execute(
        method = "PUT",
        path = path,
        serializer = serializer,
        query = query,
        jsonBody = json.encodeToString(bodySerializer, body),
        auth = auth,
    )

    suspend fun <T, TBody> putJson(
        pathSegments: List<String>,
        serializer: KSerializer<T>,
        body: TBody,
        bodySerializer: KSerializer<TBody>,
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T = execute(
        method = "PUT",
        pathSegments = pathSegments,
        serializer = serializer,
        jsonBody = json.encodeToString(bodySerializer, body),
        auth = auth,
    )

    fun buildUrl(path: String, query: Map<String, String> = emptyMap(), baseUrl: String = config.baseUrl): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
        for (segment in path.removePrefix("/").split("/")) {
            if (segment.isNotEmpty()) {
                builder.addPathSegment(segment)
            }
        }

        for ((key, value) in query) {
            builder.addQueryParameter(key, value)
        }

        return builder.build().toString()
    }

    fun buildUrl(
        pathSegments: List<String>,
        query: Map<String, String> = emptyMap(),
        baseUrl: String = config.baseUrl,
    ): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
        for (segment in pathSegments) {
            if (segment.isNotEmpty()) {
                builder.addLiteralPathSegment(segment)
            }
        }

        for ((key, value) in query) {
            builder.addQueryParameter(key, value)
        }

        return builder.build().toString()
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T {
        val url = buildUrl(path = path, query = query)
        return executeUrl(
            method = method,
            url = url,
            serializer = serializer,
            form = form,
            jsonBody = jsonBody,
            auth = auth,
        )
    }

    private suspend fun <T> execute(
        method: String,
        pathSegments: List<String>,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T {
        val url = buildUrl(pathSegments = pathSegments, query = query)
        return executeUrl(
            method = method,
            url = url,
            serializer = serializer,
            form = form,
            jsonBody = jsonBody,
            auth = auth,
        )
    }

    private suspend fun <T> executeUrl(
        method: String,
        url: String,
        serializer: KSerializer<T>,
        form: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T {
        val requestData = PutioRequestData(method = method, url = url)
        val request = buildRequest(method = method, url = url, form = form, jsonBody = jsonBody, auth = auth)
        val response = try {
            httpClient.newCall(request).await()
        } catch (cause: Exception) {
            throw PutioTransportException(requestData, cause)
        }

        response.use {
            val body = response.body.string()

            if (!response.isSuccessful) {
                throw decodeApiException(requestData, response, body)
            }

            return try {
                json.decodeFromString(serializer, body)
            } catch (cause: Exception) {
                throw PutioSerializationException(requestData, body, cause)
            }
        }
    }

    private fun buildRequest(
        method: String,
        url: String,
        form: Map<String, String>,
        jsonBody: String?,
        auth: PutioAuth,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", config.userAgent)

        resolveAuthorization(auth)?.let { builder.header("Authorization", it) }

        return when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(jsonBody?.toRequestBody(JSON_MEDIA_TYPE) ?: buildFormBody(form)).build()
            "PUT" -> builder.put(jsonBody?.toRequestBody(JSON_MEDIA_TYPE) ?: buildFormBody(form)).build()
            else -> error("Unsupported method $method")
        }
    }

    private fun buildFormBody(form: Map<String, String>): FormBody {
        val builder = FormBody.Builder()
        for ((key, value) in form) {
            builder.add(key, value)
        }

        return builder.build()
    }

    private fun resolveAuthorization(auth: PutioAuth): String? =
        when (auth) {
            PutioAuth.None -> null
            PutioAuth.ConfigToken -> {
                val token = config.accessToken
                    ?: throw PutioConfigurationException(
                        "This endpoint requires an access token, but PutioConfig.accessToken is missing",
                    )
                "Token $token"
            }
            is PutioAuth.Token -> "Token ${auth.value}"
        }

    private fun decodeApiException(
        request: PutioRequestData,
        response: Response,
        body: String,
    ): PutioApiException {
        val envelope = runCatching {
            json.decodeFromString(PutioApiErrorEnvelope.serializer(), body)
        }.getOrNull()

        val statusCode = envelope?.statusCode ?: response.code
        val errorType = envelope?.errorType
        val message = envelope?.message ?: "put.io returned HTTP $statusCode"

        return PutioApiException(
            request = request,
            resolvedStatusCode = statusCode,
            resolvedErrorType = errorType,
            envelope = envelope ?: PutioApiErrorEnvelope(message = message, statusCode = statusCode, errorType = errorType),
            responseBody = body,
            message = message,
        )
    }
}

private fun okhttp3.HttpUrl.Builder.addLiteralPathSegment(segment: String): okhttp3.HttpUrl.Builder {
    require(segment != "." && segment != "..") {
        "Path segment must not be a dot path segment"
    }

    return addPathSegment(segment)
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private suspend fun okhttp3.Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        enqueue(
            object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isCancelled) {
                        return
                    }

                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    continuation.resume(response)
                }
            },
        )

        continuation.invokeOnCancellation { cancel() }
    }
