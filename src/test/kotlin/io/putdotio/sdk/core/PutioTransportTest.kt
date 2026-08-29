package io.putdotio.sdk.core

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioSerializationException
import io.putdotio.sdk.errors.PutioTransportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PutioTransportTest {
    @Test
    fun `get with auth none omits authorization header`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            val transport = newTransport(server, accessToken = "secret-token")
            val response =
                kotlinx.coroutines.runBlocking {
                    transport.get(
                        path = "/oauth2/oob/code",
                        serializer = OkResponse.serializer(),
                        auth = PutioAuth.None,
                    )
                }

            assertEquals("OK", response.status)
            val request = server.takeRequest()
            assertEquals("/v2/oauth2/oob/code", request.target)
            assertNull(request.headers["Authorization"])
        }

    @Test
    fun `config token auth requires an access token`() =
        withServer { server ->
            val transport = newTransport(server, accessToken = null)

            val error =
                assertFailsWith<PutioConfigurationException> {
                    kotlinx.coroutines.runBlocking {
                        transport.get(
                            path = "/files/list",
                            serializer = OkResponse.serializer(),
                        )
                    }
                }

            assertEquals(
                "This endpoint requires an access token, but PutioConfig.accessToken is missing",
                error.message,
            )
        }

    @Test
    fun `transport wraps undecodable success payloads as serialization errors`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK","unexpected":true}""").build())

            val transport = newTransport(server, accessToken = "secret-token")

            val error =
                assertFailsWith<PutioSerializationException> {
                    kotlinx.coroutines.runBlocking {
                        transport.get(
                            path = "/files/list",
                            serializer = FileLikeResponse.serializer(),
                        )
                    }
                }

            assertEquals("GET", error.request.method)
            assertEquals("""{"status":"OK","unexpected":true}""", error.responseBody)
        }

    @Test
    fun `transport preserves cancellation while awaiting a response`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .onResponseStart(SocketEffect.Stall)
                    .build(),
            )

            val transport = newTransport(server, accessToken = "secret-token")
            val observedFailure = CompletableDeferred<Throwable>()

            runBlocking {
                val request =
                    launch {
                        try {
                            transport.get(
                                path = "/files/list",
                                serializer = OkResponse.serializer(),
                            )
                        } catch (cause: Throwable) {
                            observedFailure.complete(cause)
                        }
                    }

                yield()
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

                val cancellation = CancellationException("cancel transport request")
                request.cancel(cancellation)

                val error = assertIs<CancellationException>(withTimeout(5_000) { observedFailure.await() })
                assertEquals(cancellation.message, error.message)
                withTimeout(5_000) { request.join() }
            }
        }

    @Test
    fun `transport wraps response body IO failures as transport errors`() {
        val cause = IOException("response body failed")
        val transport = newTransportWithBodyFailure(cause)

        val error =
            assertFailsWith<PutioTransportException> {
                runBlocking {
                    transport.get(
                        path = "/files/list",
                        serializer = OkResponse.serializer(),
                    )
                }
            }

        assertEquals("GET", error.request.method)
        assertEquals("https://example.test/v2/files/list", error.request.url)
        assertIs<IOException>(error.cause)
        assertEquals(cause.message, error.cause?.message)
    }

    @Test
    fun `transport cancels the OkHttp call while reading a stalled response body`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"status":"OK"}""")
                    .onResponseBody(SocketEffect.Stall)
                    .build(),
            )
            val bodyReadStarted = CompletableDeferred<Unit>()
            val callCancelled = CompletableDeferred<Unit>()
            val httpClient =
                OkHttpClient
                    .Builder()
                    .eventListener(
                        object : EventListener() {
                            override fun responseBodyStart(call: Call) {
                                bodyReadStarted.complete(Unit)
                            }

                            override fun canceled(call: Call) {
                                callCancelled.complete(Unit)
                            }
                        },
                    ).build()
            val transport = newTransport(server, accessToken = "secret-token", httpClient = httpClient)
            val observedFailure = CompletableDeferred<Throwable>()

            runBlocking {
                val request =
                    launch {
                        try {
                            transport.get(
                                path = "/files/list",
                                serializer = OkResponse.serializer(),
                            )
                        } catch (cause: Throwable) {
                            observedFailure.complete(cause)
                        }
                    }

                withTimeout(5_000) { bodyReadStarted.await() }

                val cancellation = CancellationException("cancel stalled response body read")
                request.cancel(cancellation)

                withTimeout(5_000) { callCancelled.await() }
                val error = assertIs<CancellationException>(withTimeout(5_000) { observedFailure.await() })
                assertEquals(cancellation.message, error.message)
                withTimeout(5_000) { request.join() }
            }
        }

    @Test
    fun `transport falls back to http status when error payload is not json`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(502)
                    .body("bad gateway")
                    .build(),
            )

            val transport = newTransport(server, accessToken = "secret-token")

            val error =
                assertFailsWith<PutioApiException> {
                    kotlinx.coroutines.runBlocking {
                        transport.get(
                            path = "/files/list",
                            serializer = OkResponse.serializer(),
                        )
                    }
                }

            assertEquals(502, error.statusCode)
            assertEquals(null, error.errorType)
            assertEquals("put.io returned HTTP 502", error.message)
            assertEquals("bad gateway", error.responseBody)
        }

    @Test
    fun `build url joins paths and appends query parameters`() {
        val transport =
            PutioTransport(
                config = PutioConfig(baseUrl = "https://api.put.io/v2/", accessToken = "token"),
                httpClient = OkHttpClient(),
                json = PutioTransport.defaultJson,
            )

        assertEquals(
            "https://api.put.io/v2/files/10/download?oauth_token=abc",
            transport.buildUrl(path = "/files/10/download", query = mapOf("oauth_token" to "abc")),
        )
    }

    @Test
    fun `build url encodes explicit path segment separators`() {
        val transport =
            PutioTransport(
                config = PutioConfig(baseUrl = "https://api.put.io/v2/", accessToken = "token"),
                httpClient = OkHttpClient(),
                json = PutioTransport.defaultJson,
            )

        assertEquals(
            "https://api.put.io/v2/config/..%2Faccount%2Finfo",
            transport.buildUrl(pathSegments = listOf("config", "../account/info")),
        )
    }

    @Test
    fun `build url rejects explicit dot path segments`() {
        val transport =
            PutioTransport(
                config = PutioConfig(baseUrl = "https://api.put.io/v2/", accessToken = "token"),
                httpClient = OkHttpClient(),
                json = PutioTransport.defaultJson,
            )

        assertFailsWith<IllegalArgumentException> {
            transport.buildUrl(pathSegments = listOf("config", ".", ".."))
        }
    }

    private fun newTransport(
        server: MockWebServer,
        accessToken: String?,
        httpClient: OkHttpClient = OkHttpClient(),
    ) = PutioTransport(
        config =
            PutioConfig(
                baseUrl = server.url("/v2/").toString(),
                accessToken = accessToken,
            ),
        httpClient = httpClient,
        json = PutioTransport.defaultJson,
    )

    private fun newTransportWithBodyFailure(failure: Throwable): PutioTransport = newTransportWithBody(FailingResponseBody(failure))

    private fun newTransportWithBody(responseBody: ResponseBody): PutioTransport {
        val httpClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(responseBody)
                        .build()
                }.build()

        return PutioTransport(
            config = PutioConfig(baseUrl = "https://example.test/v2/", accessToken = "secret-token"),
            httpClient = httpClient,
            json = PutioTransport.defaultJson,
        )
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}

private class FailingResponseBody(
    private val failure: Throwable,
) : ResponseBody() {
    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = -1L

    override fun source(): BufferedSource =
        object : Source {
            override fun read(
                sink: Buffer,
                byteCount: Long,
            ): Long = throw failure

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }.buffer()
}

@kotlinx.serialization.Serializable
private data class FileLikeResponse(
    val files: List<String>,
    val status: String,
)
