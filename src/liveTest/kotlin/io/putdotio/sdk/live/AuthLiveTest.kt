package io.putdotio.sdk.live

import io.putdotio.sdk.auth.DeviceCodeAuthOptions
import io.putdotio.sdk.auth.DeviceCodeAuthState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AuthLiveTest {
    @Test
    fun `validateToken succeeds for configured live token`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val validation = sdk.auth.validateToken()
                assertTrue(validation.result)
                assertNotNull(validation.userId)
            }
        }
    }

    @Test
    fun `device code orchestrator reaches awaiting link and stops on a one-poll budget`() {
        runBlocking {
            val clientId = LiveSupport.requireClientId()
            LiveSupport.newAuthedClient(clientId = clientId).use { sdk ->
                // Nobody approves the code, so the attempt must end in Expired without hanging.
                val states = sdk.deviceCodeAuth.link(DeviceCodeAuthOptions(1.seconds, 1.seconds)).toList()
                assertEquals(DeviceCodeAuthState.Requesting, states.first())
                val awaiting = assertIs<DeviceCodeAuthState.AwaitingLink>(states[1])
                assertTrue(awaiting.code.isNotBlank())
                assertIs<DeviceCodeAuthState.Expired>(states.last())
            }
        }
    }

    @Test
    fun `getCode returns an out of band auth code for configured client id`() {
        runBlocking {
            val clientId = LiveSupport.requireClientId()

            LiveSupport.newAuthedClient(clientId = clientId).use { sdk ->
                val code = sdk.auth.getCode()
                assertTrue(code.code.isNotBlank())
                assertTrue(code.qrCodeUrl.isNotBlank())

                val loginUrl =
                    sdk.auth.buildLoginUrl(
                        redirectUri = "putio://live/auth",
                        state = "live-state",
                    )

                assertTrue(loginUrl.contains("client_id=$clientId"))
                assertTrue(loginUrl.contains("state=live-state"))
                assertTrue(loginUrl.contains("redirect_uri=putio%3A%2F%2Flive%2Fauth"))
                assertEquals("token", Regex("""response_type=([^&]+)""").find(loginUrl)?.groupValues?.get(1))
            }
        }
    }
}
