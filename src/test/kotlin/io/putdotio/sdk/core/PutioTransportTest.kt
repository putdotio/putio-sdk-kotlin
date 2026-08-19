package io.putdotio.sdk.core

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioSerializationException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    ) = PutioTransport(
        config =
            PutioConfig(
                baseUrl = server.url("/v2/").toString(),
                accessToken = accessToken,
            ),
        httpClient = OkHttpClient(),
        json = PutioTransport.defaultJson,
    )

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}

@kotlinx.serialization.Serializable
private data class FileLikeResponse(
    val files: List<String>,
    val status: String,
)
