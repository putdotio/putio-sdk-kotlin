package io.putdotio.sdk.auth

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.account.AccountApi
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioConfigurationException
import io.putdotio.sdk.errors.PutioOperationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class DeviceCodeAuthTest {
    @Test
    fun `happy path emits requesting, awaiting, validating, linked with the token kept out of toString`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"OK","oauth_token":null}"""))
            server.enqueue(json("""{"status":"OK","oauth_token":"tok-123"}"""))
            server.enqueue(json("""{"status":"OK","result":true,"user_id":7}"""))
            server.enqueue(json(ACCOUNT_ENVELOPE))

            val (orchestrator, sleeps) = orchestrator(server)
            val states = runBlocking { orchestrator.link(fastOptions).toList() }

            assertEquals(DeviceCodeAuthState.Requesting, states[0])
            assertEquals(DeviceCodeAuthState.AwaitingLink("ABCD", "https://example.com/qr.png", fastOptions.budget), states[1])
            assertEquals(DeviceCodeAuthState.Validating, states[2])
            val linked = assertIs<DeviceCodeAuthState.Linked>(states[3])
            assertEquals("tok-123", linked.accessToken)
            assertEquals("sdk-user", linked.account.username)
            assertEquals("Linked(account=sdk-user)", linked.toString())
            assertEquals(4, states.size)
            assertEquals(listOf(fastOptions.pollInterval, fastOptions.pollInterval), sleeps)

            assertEquals("/v2/oauth2/oob/code?app_id=tv-app&client_name=put.io%20TV", server.takeRequest().target)
            assertEquals("/v2/oauth2/oob/code/ABCD", server.takeRequest().target)
            assertEquals("/v2/oauth2/oob/code/ABCD", server.takeRequest().target)
            val validate = server.takeRequest()
            assertEquals("/v2/oauth2/validate", validate.target)
            assertEquals("Token tok-123", validate.headers["Authorization"])
            val info = server.takeRequest()
            assertEquals("/v2/account/info", info.target)
            assertEquals("Token tok-123", info.headers["Authorization"])
        }

    @Test
    fun `a 404 on the match endpoint reports code rejection and stops polling`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"ERROR","status_code":404,"error_type":"NOT_FOUND"}""", 404))

            val (orchestrator, _) = orchestrator(server)
            val states = runBlocking { orchestrator.link(fastOptions).toList() }

            assertEquals(
                DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.CODE_REJECTED),
                states.last(),
            )
            assertEquals(3, states.size)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `the budget elapsing reports expiry after exactly the polls it allows`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"OK","oauth_token":null}"""))

            val time = TestTimeSource()
            val (orchestrator, sleeps) = orchestrator(server, time) { time += it }
            val states = runBlocking { orchestrator.link(DeviceCodeAuthOptions(1.seconds, 2.seconds)).toList() }

            assertEquals(
                DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.BUDGET_ELAPSED),
                states.last(),
            )
            assertEquals(2, server.requestCount)
            assertEquals(listOf(1.seconds, 1.seconds), sleeps)
        }

    @Test
    fun `the last sleep is clipped to the remaining budget`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"OK","oauth_token":null}"""))

            val time = TestTimeSource()
            val (orchestrator, sleeps) = orchestrator(server, time) { time += it }
            runBlocking { orchestrator.link(DeviceCodeAuthOptions(3.seconds, 4.seconds)).toList() }

            assertEquals(listOf(3.seconds, 1.seconds), sleeps)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a token that arrives after the deadline is treated as expired`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"OK","oauth_token":"tok-late"}"""))

            val time = TestTimeSource()
            // The poll itself takes longer than the whole budget.
            val (orchestrator, _) = orchestrator(server, time, beforeRequest = { time += 10.seconds }) { time += it }
            val states = runBlocking { orchestrator.link(DeviceCodeAuthOptions(1.seconds, 5.seconds)).toList() }

            assertEquals(
                DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.BUDGET_ELAPSED),
                states.last(),
            )
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a poll that stalls past the budget expires the attempt without waiting for the transport`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(
                json("""{"status":"OK","oauth_token":"tok-1"}""")
                    .newBuilder()
                    .headersDelay(30, TimeUnit.SECONDS)
                    .build(),
            )

            val (orchestrator, _) = orchestrator(server, sleep = { delay(it) })
            val elapsed =
                kotlin.system.measureTimeMillis {
                    val states =
                        runBlocking {
                            orchestrator.link(DeviceCodeAuthOptions(50.milliseconds, 300.milliseconds)).toList()
                        }
                    assertEquals(
                        DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.BUDGET_ELAPSED),
                        states.last(),
                    )
                }
            assertTrue(elapsed < 5_000, "expired in ${elapsed}ms, not bounded by the 30s response delay")
        }

    @Test
    fun `a collector exception propagates instead of becoming a Failed state`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"OK","oauth_token":"tok-1"}"""))
            server.enqueue(json("""{"status":"OK","result":true,"user_id":7}"""))
            server.enqueue(json(ACCOUNT_ENVELOPE))

            val (orchestrator, _) = orchestrator(server)
            val boom = PutioConfigurationException("collector refused the token")
            val seen = mutableListOf<DeviceCodeAuthState>()
            val thrown =
                assertFailsWith<PutioConfigurationException> {
                    runBlocking {
                        orchestrator.link(fastOptions).collect { state ->
                            seen += state
                            if (state is DeviceCodeAuthState.Linked) throw boom
                        }
                    }
                }
            assertSame(boom, thrown)
            assertIs<DeviceCodeAuthState.Linked>(seen.last())
            assertEquals(4, seen.size)
        }

    @Test
    fun `cancelling the collector during the poll delay stops without a terminal state`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            val seen = mutableListOf<DeviceCodeAuthState>()
            val (orchestrator, _) = orchestrator(server, sleep = { delay(it) })
            runBlocking {
                val job =
                    launch {
                        orchestrator.link(DeviceCodeAuthOptions(10.seconds, 1.minutes)).collect { seen += it }
                    }
                withTimeout(5.seconds) {
                    while (seen.size < 2) yield()
                }
                job.cancelAndJoin()
            }
            assertIs<DeviceCodeAuthState.AwaitingLink>(seen.last())
            assertEquals(2, seen.size)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `cancelling the collector during an in-flight poll stops without a terminal state`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(
                json(
                    """{"status":"OK","oauth_token":"tok-1"}""",
                ).newBuilder().headersDelay(30, TimeUnit.SECONDS).build(),
            )
            val seen = mutableListOf<DeviceCodeAuthState>()
            val (orchestrator, _) = orchestrator(server)
            runBlocking {
                val job = launch { orchestrator.link(fastOptions).collect { seen += it } }
                withTimeout(5.seconds) {
                    while (server.requestCount < 2) yield()
                }
                job.cancelAndJoin()
            }
            assertIs<DeviceCodeAuthState.AwaitingLink>(seen.last())
            assertEquals(2, seen.size)
        }

    @Test
    fun `transport failures while polling are retried until the budget ends`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
            server.enqueue(json("""{"status":"OK","oauth_token":"tok-9"}"""))
            server.enqueue(json("""{"status":"OK","result":true,"user_id":7}"""))
            server.enqueue(json(ACCOUNT_ENVELOPE))

            val (orchestrator, _) = orchestrator(server)
            val states = runBlocking { orchestrator.link(fastOptions).toList() }

            assertIs<DeviceCodeAuthState.Linked>(states.last())
            assertEquals(5, server.requestCount)
        }

    @Test
    fun `non-transport api failures while polling fail the attempt`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"ERROR","status_code":429,"error_type":"RATE_LIMITED"}""", 429))

            val (orchestrator, _) = orchestrator(server)
            val states = runBlocking { orchestrator.link(fastOptions).toList() }

            val failed = assertIs<DeviceCodeAuthState.Failed>(states.last())
            val operation = assertIs<PutioOperationException>(failed.error)
            assertEquals("checkCodeMatch", operation.operation)
        }

    @Test
    fun `a rejected validation reports code rejection and a failed code request fails`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"OK","oauth_token":"tok-bad"}"""))
            server.enqueue(json("""{"status":"OK","result":false}"""))

            val (orchestrator, _) = orchestrator(server)
            val rejected = runBlocking { orchestrator.link(fastOptions).toList() }
            assertEquals(
                DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.CODE_REJECTED),
                rejected.last(),
            )

            server.enqueue(json("""{"status":"ERROR","status_code":500,"error_type":"SERVER"}""", 500))
            val failed = runBlocking { orchestrator.link(fastOptions).toList() }
            assertEquals(DeviceCodeAuthState.Requesting, failed[0])
            val failure = assertIs<DeviceCodeAuthState.Failed>(failed[1])
            assertEquals("getCode", assertIs<PutioOperationException>(failure.error).operation)
            assertEquals(2, failed.size)
        }

    @Test
    fun `account info load failure after validation fails the attempt`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"OK","oauth_token":"tok-1"}"""))
            server.enqueue(json("""{"status":"OK","result":true,"user_id":7}"""))
            server.enqueue(json("""{"status":"ERROR","status_code":503,"error_type":"DOWN"}""", 503))

            val (orchestrator, _) = orchestrator(server)
            val states = runBlocking { orchestrator.link(fastOptions).toList() }
            val failure = assertIs<DeviceCodeAuthState.Failed>(states.last())
            assertEquals("getInfo", assertIs<PutioOperationException>(failure.error).operation)
        }

    @Test
    fun `options reject a non-positive interval or a budget that allows no poll`() {
        assertFailsWith<IllegalArgumentException> { DeviceCodeAuthOptions(pollInterval = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { DeviceCodeAuthOptions(pollInterval = 3.seconds, budget = 3.seconds) }
    }

    @Test
    fun `default options poll every three seconds for five minutes`() {
        assertEquals(DeviceCodeAuthOptions(pollInterval = 3.seconds, budget = 5.minutes), DeviceCodeAuthOptions())
    }

    @Test
    fun `the client exposes the orchestrator over its own auth and account namespaces`() =
        withServer { server ->
            server.enqueue(json(CODE_ENVELOPE))
            server.enqueue(json("""{"status":"ERROR","status_code":404,"error_type":"NOT_FOUND"}""", 404))
            val states =
                runBlocking {
                    PutioClient(PutioConfig(clientId = "tv-app", baseUrl = server.url("/v2/").toString())).use { sdk ->
                        sdk.deviceCodeAuth.link(DeviceCodeAuthOptions(1.milliseconds, 10.seconds)).toList()
                    }
                }
            assertEquals(
                DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.CODE_REJECTED),
                states.last(),
            )
            assertEquals(2, server.requestCount)
        }

    private val fastOptions = DeviceCodeAuthOptions(pollInterval = 10.milliseconds, budget = 10.seconds)

    private fun orchestrator(
        server: MockWebServer,
        timeSource: TestTimeSource = TestTimeSource(),
        beforeRequest: () -> Unit = {},
        sleep: suspend (Duration) -> Unit = {},
    ): Pair<DeviceCodeAuth, MutableList<Duration>> {
        val sleeps = mutableListOf<Duration>()
        val httpClient =
            okhttp3.OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    beforeRequest()
                    chain.proceed(chain.request())
                }.build()
        val transport =
            PutioTransport(
                config = PutioConfig(clientId = "tv-app", clientName = "put.io TV", baseUrl = server.url("/v2/").toString()),
                httpClient = httpClient,
                json = PutioTransport.defaultJson,
            )
        val orchestrator =
            DeviceCodeAuth(
                auth = AuthApi(transport),
                account = AccountApi(transport),
                timeSource = timeSource,
                sleep = { duration ->
                    sleeps += duration
                    sleep(duration)
                },
            )
        return orchestrator to sleeps
    }

    private fun json(
        body: String,
        code: Int = 200,
    ): MockResponse =
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build()

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private companion object {
        const val CODE_ENVELOPE = """{"status":"OK","code":"ABCD","qr_code_url":"https://example.com/qr.png"}"""
        const val ACCOUNT_ENVELOPE = """{"status":"OK","info":{
            "user_id":7,"username":"sdk-user","mail":"sdk@example.com","avatar_url":"https://example.com/a.png",
            "disk":{"avail":1,"size":2,"used":1},"settings":{"sort_by":"NAME_ASC"},"account_status":"active"}}"""
    }
}
