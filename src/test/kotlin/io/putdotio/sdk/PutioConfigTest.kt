package io.putdotio.sdk

import io.putdotio.sdk.core.PutioTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PutioConfigTest {
    @Test
    fun `access token keeps the public data class API with volatile visibility`() {
        val config = PutioConfig(accessToken = "initial-token")
        val mutableFields =
            listOf(
                "accessToken",
                "clientId",
                "clientName",
                "baseUrl",
                "webAppUrl",
                "userAgent",
                "uploadBaseUrl",
            )
        mutableFields.forEach { fieldName ->
            val field = PutioConfig::class.java.getDeclaredField(fieldName)
            assertTrue(Modifier.isVolatile(field.modifiers), "$fieldName must be @Volatile")
        }
        assertEquals("initial-token", config.component1())
        assertEquals("copied-token", config.copy(accessToken = "copied-token").accessToken)

        PutioClient(config = config, okHttpClient = OkHttpClient()).use { client ->
            assertSame(config, client.config)

            client.setAccessToken("updated-token")
            assertEquals("updated-token", config.accessToken)

            client.clearAccessToken()
            assertEquals(null, config.accessToken)
        }
    }

    @Test
    fun `compareAndSetAccessToken leaves a token it did not expect`() {
        PutioClient(config = PutioConfig(accessToken = "rejected"), okHttpClient = OkHttpClient()).use { client ->
            client.setAccessToken("fresh")

            assertFalse(client.compareAndSetAccessToken(expected = "rejected", token = null))
            assertEquals("fresh", client.config.accessToken)

            assertTrue(client.compareAndSetAccessToken(expected = "fresh", token = null))
            assertNull(client.config.accessToken)
        }
    }

    @Test
    fun `concurrent compareAndSetAccessToken lets exactly one writer win`() {
        val writers = 64
        val config = PutioConfig()
        val pool = Executors.newFixedThreadPool(writers)

        try {
            repeat(ROUNDS) {
                config.accessToken = null
                val start = CountDownLatch(1)
                val wins =
                    (0 until writers)
                        .map { index ->
                            pool.submit<String?> {
                                start.await()
                                "token-$index".takeIf { config.compareAndSetAccessToken(expected = null, token = it) }
                            }
                        }.also { start.countDown() }
                        .mapNotNull { it.get(5, TimeUnit.SECONDS) }

                assertEquals(1, wins.size)
                assertEquals(wins.single(), config.accessToken)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent requests observe access token updates through the public client`() {
        val requestIndex = AtomicInteger(0)
        val firstAuthorization = CompletableDeferred<String?>()
        val secondAuthorization = CompletableDeferred<String?>()
        val releaseFirstRequest = CountDownLatch(1)
        val httpClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    when (requestIndex.getAndIncrement()) {
                        0 -> {
                            firstAuthorization.complete(chain.request().header("Authorization"))
                            releaseFirstRequest.await(5, TimeUnit.SECONDS)
                        }

                        1 -> {
                            secondAuthorization.complete(chain.request().header("Authorization"))
                        }
                    }

                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"result":true}""".toResponseBody("application/json".toMediaType()),
                        ).build()
                }.build()
        val config = PutioConfig(accessToken = "initial-token", baseUrl = "https://example.test/v2/")

        try {
            PutioClient(config = config, okHttpClient = httpClient).use { client ->
                runBlocking {
                    val firstRequest = async { client.auth.validateToken() }

                    try {
                        assertEquals("Token initial-token", withTimeout(5_000) { firstAuthorization.await() })

                        client.setAccessToken("updated-token")
                        val secondRequest = async { client.auth.validateToken() }

                        assertEquals("Token updated-token", withTimeout(5_000) { secondAuthorization.await() })
                        assertTrue(withTimeout(5_000) { secondRequest.await() }.result)
                    } finally {
                        releaseFirstRequest.countDown()
                    }

                    assertTrue(withTimeout(5_000) { firstRequest.await() }.result)
                }
            }
        } finally {
            releaseFirstRequest.countDown()
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        }
    }

    @Test
    fun `default okHttpClient is allocated lazily on the first HTTP call`() {
        val allocations = AtomicInteger(0)
        val builtClients = mutableListOf<OkHttpClient>()
        val factory = {
            allocations.incrementAndGet()
            defaultOkHttpClient().also { builtClients += it }
        }

        PutioClient(
            config = PutioConfig(accessToken = "token", clientId = "android-app"),
            okHttpClient = null,
            json = PutioTransport.defaultJson,
            defaultHttpClientFactory = factory,
        ).use { urlOnlyClient ->
            assertEquals("https://api.put.io/v2/files/7/download", urlOnlyClient.files.buildDownloadRequestUrl(7))
            urlOnlyClient.auth.buildLoginUrl(redirectUri = "putio://cb", state = "s")
            urlOnlyClient.grants.buildIconRequest(7)
            assertEquals(0, allocations.get())
        }
        assertEquals(0, allocations.get())

        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("""{"result":true}""").build())
            server.enqueue(MockResponse.Builder().body("""{"result":true}""").build())

            PutioClient(
                config = PutioConfig(accessToken = "token", baseUrl = server.url("/v2/").toString()),
                okHttpClient = null,
                json = PutioTransport.defaultJson,
                defaultHttpClientFactory = factory,
            ).use { activeClient ->
                assertEquals(0, allocations.get())
                val validation = runBlocking { activeClient.auth.validateToken() }
                assertTrue(validation.result)
                assertEquals(1, allocations.get())
            }
            assertTrue(
                builtClients
                    .single()
                    .dispatcher.executorService.isShutdown,
            )

            val callerClient = OkHttpClient()
            try {
                PutioClient(
                    config = PutioConfig(accessToken = "token", baseUrl = server.url("/v2/").toString()),
                    okHttpClient = callerClient,
                    json = PutioTransport.defaultJson,
                    defaultHttpClientFactory = factory,
                ).use { borrowedClient ->
                    val validation = runBlocking { borrowedClient.auth.validateToken() }
                    assertTrue(validation.result)
                }
                assertEquals(1, allocations.get())
                assertFalse(callerClient.dispatcher.executorService.isShutdown)
            } finally {
                callerClient.dispatcher.executorService.shutdown()
                callerClient.connectionPool.evictAll()
            }
        }
    }

    private companion object {
        const val ROUNDS = 200
    }
}
