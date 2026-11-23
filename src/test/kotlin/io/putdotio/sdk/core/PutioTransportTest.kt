package io.putdotio.sdk.core

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.account.AccountSettingsPatch
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.errors.PutioSerializationException
import io.putdotio.sdk.errors.PutioTransportException
import io.putdotio.sdk.errors.PutioTransportFailureKind
import io.putdotio.sdk.files.PutioFolderType
import io.putdotio.sdk.transfers.TransferStatus
import io.putdotio.sdk.transfers.TransferType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PutioTransportTest {
    @Test
    fun `get with pathSegments encodes literal segments and appends query`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            val transport = newTransport(server, accessToken = "secret-token")
            val response =
                runBlocking {
                    transport.get(
                        pathSegments = listOf("oauth2", "oob", "code", "../escape"),
                        serializer = OkResponse.serializer(),
                        query = mapOf("k" to "v"),
                        auth = PutioAuth.None,
                    )
                }

            assertEquals("OK", response.status)
            val request = server.takeRequest()
            assertEquals("/v2/oauth2/oob/code/..%2Fescape?k=v", request.target)
            assertNull(request.headers["Authorization"])
        }

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
    fun `default json coerces explicit nulls on defaulted fields and keeps required fields strict`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "transfers": [
                            {
                              "id": 1,
                              "name": "x",
                              "type": null,
                              "status": null,
                              "save_parent_id": null,
                              "source": null,
                              "links": null,
                              "is_private": null,
                              "created_at": null
                            }
                          ],
                          "status": "OK"
                        }
                        """.trimIndent(),
                    ).build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "file": {
                            "id": 9,
                            "name": "Folder",
                            "size": null,
                            "created_at": "2026-09-06",
                            "file_type": "FOLDER",
                            "folder_type": null,
                            "is_shared": null
                          },
                          "status": "OK"
                        }
                        """.trimIndent(),
                    ).build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"transfers":[{"id":1,"name":null,"status":"COMPLETED"}],"status":"OK"}""")
                    .build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"transfers":[],"status":null}""")
                    .build(),
            )

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "secret-token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val transfer =
                        sdk.transfers
                            .list()
                            .transfers
                            .single()
                    assertEquals(TransferType.NOT_AVAILABLE, transfer.type)
                    assertEquals(TransferStatus.WAITING, transfer.status)
                    assertEquals(0L, transfer.saveParentId)
                    assertEquals("", transfer.source)
                    assertEquals(emptyList(), transfer.links)
                    assertEquals(false, transfer.isPrivate)
                    assertEquals("", transfer.createdAt)

                    val file = sdk.files.get(9)
                    assertEquals(0L, file.size)
                    assertEquals(PutioFolderType.REGULAR, file.folderType)
                    assertEquals(false, file.isShared)

                    val missingTransferName =
                        assertFailsWith<PutioOperationException> {
                            sdk.transfers.list()
                        }
                    assertIs<PutioSerializationException>(missingTransferName.underlyingError)

                    val missingStatus =
                        assertFailsWith<PutioOperationException> {
                            sdk.transfers.list()
                        }
                    assertIs<PutioSerializationException>(missingStatus.underlyingError)
                }
            }
        }

    @Test
    fun `transport wraps malformed success bodies as serialization errors`() =
        listOf("", "not json", """{"status":"OK"""", """["OK"]""").forEach { body ->
            withServer { server ->
                server.enqueue(MockResponse.Builder().body(body).build())

                val transport = newTransport(server, accessToken = "secret-token")

                val error =
                    assertFailsWith<PutioSerializationException>("body: $body") {
                        runBlocking { transport.get(path = "/files/list", serializer = OkResponse.serializer()) }
                    }

                assertNotNull(error.cause)
                assertEquals("GET", error.request.method)
            }
        }

    @Test
    fun `transport wraps a refused connection as a transport error`() {
        val baseUrl =
            MockWebServer().use { server ->
                server.start()
                server.url("/v2/").toString()
            }
        val transport =
            PutioTransport(
                config = PutioConfig(baseUrl = baseUrl, accessToken = "secret-token"),
                httpClient = OkHttpClient(),
                json = PutioTransport.defaultJson,
            )

        val error =
            assertFailsWith<PutioTransportException> {
                runBlocking { transport.get(path = "/files/list", serializer = OkResponse.serializer()) }
            }

        assertEquals("${baseUrl}files/list", error.request.url)
        assertEquals(PutioTransportFailureKind.CONNECTION, error.failureKind)
        assertIs<ConnectException>(error.cause)
    }

    @Test
    fun `transport wraps a read timeout as a transport error`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .onResponseStart(SocketEffect.Stall)
                    .build(),
            )
            val httpClient =
                OkHttpClient
                    .Builder()
                    .readTimeout(200, TimeUnit.MILLISECONDS)
                    .build()
            val transport = newTransport(server, accessToken = "secret-token", httpClient = httpClient)

            val error =
                assertFailsWith<PutioTransportException> {
                    runBlocking { transport.get(path = "/files/list", serializer = OkResponse.serializer()) }
                }

            assertEquals(PutioTransportFailureKind.TIMEOUT, error.failureKind)
            assertIs<SocketTimeoutException>(error.cause)
        }

    @Test
    fun `form post is not retried after socket reset on response start`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
            server.enqueue(
                MockResponse
                    .Builder()
                    .onResponseStart(SocketEffect.CloseSocket())
                    .build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"status":"OK","file":{"id":1,"name":"dup","created_at":"2026-09-06","file_type":"FOLDER"}}""")
                    .build(),
            )

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                accessToken = "secret-token",
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.files.resetStartFrom(1)
                            sdk.files.createFolder(name = "dup", parentId = 0)
                        }
                    }
                }

            assertIs<PutioTransportException>(error.underlyingError)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `json post is not retried after socket reset on response start`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
            server.enqueue(
                MockResponse
                    .Builder()
                    .onResponseStart(SocketEffect.CloseSocket())
                    .build(),
            )
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking {
                        PutioClient(
                            PutioConfig(
                                accessToken = "secret-token",
                                baseUrl = server.url("/v2/").toString(),
                            ),
                        ).use { sdk ->
                            sdk.files.resetStartFrom(1)
                            sdk.account.saveSettings(AccountSettingsPatch(historyEnabled = false))
                        }
                    }
                }

            assertIs<PutioTransportException>(error.underlyingError)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `post is not retried on 408 or 503 with zero retry-after`() =
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(408)
                    .body("""{"status":"ERROR","status_code":408}""")
                    .build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(503)
                    .addHeader("Retry-After", "0")
                    .body("""{"status":"ERROR","status_code":503}""")
                    .build(),
            )
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            runBlocking {
                PutioClient(
                    PutioConfig(
                        accessToken = "secret-token",
                        baseUrl = server.url("/v2/").toString(),
                    ),
                ).use { sdk ->
                    val timeoutError =
                        assertFailsWith<PutioOperationException> {
                            sdk.account.saveSettings(AccountSettingsPatch(historyEnabled = false))
                        }
                    assertEquals(408, assertIs<PutioApiException>(timeoutError.underlyingError).statusCode)

                    val unavailableError =
                        assertFailsWith<PutioOperationException> {
                            sdk.account.saveSettings(AccountSettingsPatch(historyEnabled = false))
                        }
                    assertEquals(503, assertIs<PutioApiException>(unavailableError.underlyingError).statusCode)
                }
            }

            assertEquals(2, server.requestCount)
        }

    @Test
    fun `idempotent get still retries after pooled socket reset`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
            server.enqueue(
                MockResponse
                    .Builder()
                    .onResponseStart(SocketEffect.CloseSocket())
                    .build(),
            )
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            val result =
                runBlocking {
                    PutioClient(
                        PutioConfig(
                            accessToken = "secret-token",
                            baseUrl = server.url("/v2/").toString(),
                        ),
                    ).use { sdk ->
                        sdk.files.resetStartFrom(1)
                        sdk.files.resetStartFrom(2)
                    }
                }

            assertEquals("OK", result.status)
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `default client bounds callTimeout and preserves caller okHttpClient settings`() =
        withServer { server ->
            PutioClient().use { defaultSdk ->
                assertEquals(30_000, defaultSdk.defaultCallTimeoutMillis)
            }
            PutioClient(okHttpClient = OkHttpClient()).use { unboundedCallerSdk ->
                assertEquals(0, unboundedCallerSdk.defaultCallTimeoutMillis)
            }
            val customClient =
                OkHttpClient
                    .Builder()
                    .callTimeout(5, TimeUnit.SECONDS)
                    .build()
            try {
                PutioClient(okHttpClient = customClient).use { customCallerSdk ->
                    assertEquals(5_000, customCallerSdk.defaultCallTimeoutMillis)
                }
            } finally {
                customClient.dispatcher.executorService.shutdown()
                customClient.connectionPool.evictAll()
            }

            server.enqueue(
                MockResponse
                    .Builder()
                    .headersDelay(500, TimeUnit.MILLISECONDS)
                    .body("""{"status":"OK"}""")
                    .build(),
            )
            val boundedClient =
                OkHttpClient
                    .Builder()
                    .callTimeout(150, TimeUnit.MILLISECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
            try {
                val transport = newTransport(server, accessToken = "secret-token", httpClient = boundedClient)
                val error =
                    assertFailsWith<PutioTransportException> {
                        runBlocking { transport.get(path = "/files/list", serializer = OkResponse.serializer()) }
                    }
                assertEquals(PutioTransportFailureKind.INTERRUPTED, error.failureKind)
            } finally {
                boundedClient.dispatcher.executorService.shutdown()
                boundedClient.connectionPool.evictAll()
            }
        }

    @Test
    fun `transport decodes responses off the caller's thread`() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())

            val transport = newTransport(server, accessToken = "secret-token")
            val decodingThreads = mutableListOf<Thread>()
            val serializer = ThreadRecordingSerializer(OkResponse.serializer(), decodingThreads)
            val caller = Executors.newSingleThreadExecutor()
            val callerThread = caller.submit<Thread> { Thread.currentThread() }.get()

            try {
                runBlocking(caller.asCoroutineDispatcher()) {
                    transport.get(path = "/account/info", serializer = serializer)
                }
            } finally {
                caller.shutdown()
            }

            assertEquals(1, decodingThreads.size)
            assertNotEquals(callerThread, decodingThreads.single())
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

            val callCancelled = CompletableDeferred<Unit>()
            val httpClient =
                OkHttpClient
                    .Builder()
                    .eventListener(
                        object : EventListener() {
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

                yield()
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

                val cancellation = CancellationException("cancel transport request")
                request.cancel(cancellation)

                withTimeout(5_000) { callCancelled.await() }
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
            assertNull(error.errorMessage)
            assertEquals("<redacted response body>", error.responseBody)
        }

    @Test
    fun `api errors expose put io's error_message`() =
        listOf(
            // Captured from api.put.io: GET /v2/files/list?sort_by=BOGUS and ?parent_id=999999999999.
            Triple(400, INVALID_SORT_ENVELOPE, "invalid sort_by param"),
            Triple(404, NOT_FOUND_ENVELOPE, NOT_FOUND_MESSAGE),
        ).forEach { (status, body, expected) ->
            val error = failWith(status, body)

            assertEquals(status, error.statusCode)
            assertEquals(expected, error.errorMessage)
            assertEquals(expected, error.envelope.errorMessage)
            assertEquals(expected, error.message)
            assertNull(error.envelope.message)
        }

    @Test
    fun `api errors keep reading a message-only envelope`() {
        val error = failWith(400, """{"status":"ERROR","status_code":400,"message":"invalid cursor"}""")

        assertEquals("invalid cursor", error.errorMessage)
        assertEquals("invalid cursor", error.message)
    }

    @Test
    fun `api error messages are redacted`() {
        val error =
            failWith(
                400,
                """{"error_message":"Bad next https://example.test/cb?oauth_token=leak","error_type":"BadRequest",""" +
                    """"status":"ERROR","status_code":400}""",
            )

        assertEquals("Bad next https://example.test/cb?oauth_token=REDACTED", error.errorMessage)
        assertEquals(error.errorMessage, error.message)
    }

    private fun failWith(
        status: Int,
        body: String,
    ): PutioApiException {
        var error: PutioApiException? = null
        withServer { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(status)
                    .body(body)
                    .build(),
            )
            val transport = newTransport(server, accessToken = "secret-token")
            error =
                assertFailsWith<PutioApiException> {
                    runBlocking { transport.get(path = "/files/list", serializer = OkResponse.serializer()) }
                }
        }
        return requireNotNull(error)
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

    @Test
    fun `build url round-trips arbitrary path segments and query parameters`() {
        val transport =
            PutioTransport(
                config = PutioConfig(baseUrl = "https://api.put.io/v2/", accessToken = "token"),
                httpClient = OkHttpClient(),
                json = PutioTransport.defaultJson,
            )
        val random = Random(20261021)

        repeat(500) { iteration ->
            val segments = List(random.nextInt(0, 5)) { random.urlText(excluding = setOf(".", "..")) }
            val query = List(random.nextInt(0, 4)) { random.urlText() to random.urlText() }.toMap()
            val expectedQuery = query.toList()
            val expectedPath = basePathPlus(segments)

            val literal = transport.buildUrl(pathSegments = segments, query = query).toHttpUrl()
            assertEquals(expectedPath, literal.pathSegments, "literal segments, iteration $iteration: $segments")
            assertEquals(expectedQuery, literal.queryPairs(), "literal query, iteration $iteration: $query")

            val slashFree = segments.map { it.replace("/", "") }.filter { it != "." && it != ".." }
            val joined = transport.buildUrl(path = slashFree.joinToString("/", prefix = "/"), query = query).toHttpUrl()
            assertEquals(basePathPlus(slashFree), joined.pathSegments, "path, iteration $iteration: $slashFree")
            assertEquals(expectedQuery, joined.queryPairs(), "path query, iteration $iteration: $query")
        }
    }

    // The base URL's trailing slash stays an empty last segment until a segment replaces it.
    private fun basePathPlus(segments: List<String>): List<String> =
        listOf("v2") + segments.filter(String::isNotEmpty).ifEmpty { listOf("") }

    private fun HttpUrl.queryPairs(): List<Pair<String, String?>> =
        (0 until querySize).map {
            queryParameterName(it) to
                queryParameterValue(it)
        }

    // Printable ASCII covers every reserved URL character; the rest add multi-byte and surrogate-pair code points.
    private val urlAlphabet = (0x20..0x7e).map { it.toChar().toString() } + listOf("é", "ş", "İ", "中", "\uD83D\uDE00", "%2e", "%2F")

    private fun Random.urlText(excluding: Set<String> = emptySet()): String {
        while (true) {
            val text = List(nextInt(0, 8)) { urlAlphabet[nextInt(urlAlphabet.size)] }.joinToString("")
            if (text !in excluding) return text
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

private const val INVALID_SORT_ENVELOPE =
    """{"error_id":null,"error_message":"invalid sort_by param","error_type":"BadRequest",""" +
        """"error_uri":"http://api.put.io/v2/docs","extra":{},"status":"ERROR","status_code":400}"""

private const val NOT_FOUND_MESSAGE =
    "The requested URL was not found on the server. " +
        "If you entered the URL manually please check your spelling and try again."

private const val NOT_FOUND_ENVELOPE =
    """{"error_id":null,"error_message":"$NOT_FOUND_MESSAGE","error_type":"NotFound",""" +
        """"error_uri":"http://api.put.io/v2/docs","extra":{},"status":"ERROR","status_code":404}"""

private class ThreadRecordingSerializer<T>(
    private val delegate: KSerializer<T>,
    private val threads: MutableList<Thread>,
) : KSerializer<T> by delegate {
    override fun deserialize(decoder: Decoder): T {
        synchronized(threads) { threads += Thread.currentThread() }
        return delegate.deserialize(decoder)
    }

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) = delegate.serialize(encoder, value)
}
