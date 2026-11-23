package io.putdotio.sdk.core

import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiErrorEnvelope
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioRequestData
import io.putdotio.sdk.errors.PutioSerializationException
import io.putdotio.sdk.errors.PutioTransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal sealed interface PutioAuth {
    data object ConfigToken : PutioAuth

    data class Token(
        val value: String,
    ) : PutioAuth

    data object None : PutioAuth
}

internal class PutioMultipartFile(
    val fieldName: String,
    val fileName: String,
    val mediaType: String,
    val content: ByteArray,
)

internal class PutioTransport(
    internal val config: PutioConfig,
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val exemptMultipartCallTimeout: Boolean = false,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    companion object {
        val defaultJson =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            }
    }

    suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T =
        execute(
            method = "GET",
            path = path,
            serializer = serializer,
            query = query,
            auth = auth,
        )

    suspend fun <T> get(
        pathSegments: List<String>,
        serializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T =
        execute(
            method = "GET",
            pathSegments = pathSegments,
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
    ): T =
        execute(
            method = "POST",
            path = path,
            serializer = serializer,
            query = query,
            form = form,
            auth = auth,
        )

    suspend fun <T> delete(
        path: String,
        serializer: KSerializer<T>,
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T =
        execute(
            method = "DELETE",
            path = path,
            serializer = serializer,
            auth = auth,
        )

    suspend fun <T> delete(
        pathSegments: List<String>,
        serializer: KSerializer<T>,
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T =
        execute(
            method = "DELETE",
            pathSegments = pathSegments,
            serializer = serializer,
            auth = auth,
        )

    suspend fun <T, TBody> postJson(
        path: String,
        serializer: KSerializer<T>,
        body: TBody,
        bodySerializer: KSerializer<TBody>,
        query: Map<String, String> = emptyMap(),
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T =
        execute(
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
    ): T =
        execute(
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
    ): T =
        execute(
            method = "PUT",
            pathSegments = pathSegments,
            serializer = serializer,
            jsonBody = json.encodeToString(bodySerializer, body),
            auth = auth,
        )

    suspend fun <T> postMultipart(
        baseUrl: String,
        path: String,
        serializer: KSerializer<T>,
        form: Map<String, String>,
        file: PutioMultipartFile,
        auth: PutioAuth = PutioAuth.ConfigToken,
    ): T {
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .apply { form.forEach { (key, value) -> addFormDataPart(key, value) } }
                .addFormDataPart(
                    file.fieldName,
                    file.fileName,
                    file.content.toRequestBody(file.mediaType.toMediaType()),
                ).build()
        return executeUrl(
            method = "POST",
            url = buildUrl(path = path, baseUrl = baseUrl),
            serializer = serializer,
            auth = auth,
            body = body,
            exemptCallTimeout = exemptMultipartCallTimeout,
        )
    }

    fun buildUrl(
        path: String,
        query: Map<String, String> = emptyMap(),
        baseUrl: String = config.baseUrl,
    ): String {
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
        body: RequestBody? = null,
        exemptCallTimeout: Boolean = false,
    ): T {
        val requestData = PutioRequestData(method = method, url = url)
        val request =
            buildRequest(method = method, url = url, form = form, jsonBody = jsonBody, auth = auth, body = body)
        val response =
            try {
                val call = httpClient.newCall(request)
                if (exemptCallTimeout) {
                    call.timeout().clearTimeout()
                }
                call.awaitResponseBody(clockMillis)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (cause: Exception) {
                throw PutioTransportException(requestData, cause)
            }

        return withContext(Dispatchers.Default) {
            if (!response.isSuccessful) {
                throw decodeApiException(
                    request = requestData,
                    responseStatusCode = response.statusCode,
                    body = response.body,
                    retryAfterSeconds = response.retryAfterSeconds,
                )
            }

            try {
                json.decodeFromString(serializer, response.body)
            } catch (cause: Exception) {
                throw PutioSerializationException(requestData, response.body, cause)
            }
        }
    }

    private fun buildRequest(
        method: String,
        url: String,
        form: Map<String, String>,
        jsonBody: String?,
        auth: PutioAuth,
        body: RequestBody?,
    ): Request {
        val builder =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", config.userAgent)

        resolveAuthorization(auth)?.let { builder.header("Authorization", it) }

        return when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(OneShotRequestBody(body ?: jsonBody?.toRequestBody(JSON_MEDIA_TYPE) ?: buildFormBody(form))).build()
            "PUT" -> builder.put(jsonBody?.toRequestBody(JSON_MEDIA_TYPE) ?: buildFormBody(form)).build()
            "DELETE" -> builder.delete().build()
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

    internal fun resolveAuthorization(auth: PutioAuth): String? =
        when (auth) {
            PutioAuth.None -> {
                null
            }

            PutioAuth.ConfigToken -> {
                val token =
                    config.accessToken
                        ?: throw PutioConfigurationException(
                            "This endpoint requires an access token, but PutioConfig.accessToken is missing",
                        )
                "Token $token"
            }

            is PutioAuth.Token -> {
                "Token ${auth.value}"
            }
        }

    private fun decodeApiException(
        request: PutioRequestData,
        responseStatusCode: Int,
        body: String,
        retryAfterSeconds: Long?,
    ): PutioApiException {
        val envelope =
            runCatching {
                json.decodeFromString(PutioApiErrorEnvelope.serializer(), body)
            }.getOrNull()

        val statusCode = envelope?.statusCode ?: responseStatusCode
        val errorType = envelope?.errorType
        val message = envelope?.errorMessage ?: envelope?.message ?: "put.io returned HTTP $statusCode"

        return PutioApiException(
            request = request,
            resolvedStatusCode = statusCode,
            httpStatusCode = responseStatusCode,
            resolvedErrorType = errorType,
            envelope = envelope ?: PutioApiErrorEnvelope(statusCode = statusCode, errorType = errorType),
            responseBody = body,
            message = message,
            retryAfterSeconds = retryAfterSeconds,
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

private class OneShotRequestBody(
    private val delegate: RequestBody,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        delegate.writeTo(sink)
    }
}

internal fun parseRetryAfterSeconds(
    headers: Headers,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val raw = headers["Retry-After"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.all { it in '0'..'9' }) {
        return raw.toLongOrNull()
    }
    val retryAtMillis = headers.getDate("Retry-After")?.time ?: return null
    val deltaMillis = retryAtMillis - nowMillis
    return if (deltaMillis <= 0L) 0L else (deltaMillis - 1L) / 1_000L + 1L
}

private data class PutioHttpResponse(
    val statusCode: Int,
    val isSuccessful: Boolean,
    val body: String,
    val retryAfterSeconds: Long?,
)

private suspend fun okhttp3.Call.awaitResponseBody(clockMillis: () -> Long): PutioHttpResponse =
    suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)

        enqueue(
            object : okhttp3.Callback {
                override fun onFailure(
                    call: okhttp3.Call,
                    e: IOException,
                ) {
                    if (completed.compareAndSet(false, true)) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: Response,
                ) {
                    response.use {
                        val result =
                            try {
                                PutioHttpResponse(
                                    statusCode = response.code,
                                    isSuccessful = response.isSuccessful,
                                    body = response.body.string(),
                                    retryAfterSeconds =
                                        if (response.isSuccessful) {
                                            null
                                        } else {
                                            parseRetryAfterSeconds(response.headers, clockMillis())
                                        },
                                )
                            } catch (cause: Exception) {
                                if (completed.compareAndSet(false, true)) {
                                    continuation.resumeWithException(cause)
                                }
                                return
                            }

                        if (completed.compareAndSet(false, true)) {
                            continuation.resume(result)
                        }
                    }
                }
            },
        )

        continuation.invokeOnCancellation {
            completed.compareAndSet(false, true)
            cancel()
        }
    }
