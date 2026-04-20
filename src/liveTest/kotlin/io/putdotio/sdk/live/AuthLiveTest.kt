package io.putdotio.sdk.live

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthLiveTest {
    @Test
    fun `validateToken succeeds for configured live token`() = runBlocking {
        LiveSupport.newAuthedClient().use { sdk ->
            val validation = sdk.auth.validateToken()
            assertTrue(validation.result)
            assertNotNull(validation.userId)
        }
    }

    @Test
    fun `getCode returns an out of band auth code for configured client id`() = runBlocking {
        val clientId = LiveSupport.requireClientId()

        LiveSupport.newAuthedClient(clientId = clientId).use { sdk ->
            val code = sdk.auth.getCode()
            assertTrue(code.code.isNotBlank())
            assertTrue(code.qrCodeUrl.isNotBlank())

            val loginUrl = sdk.auth.buildLoginUrl(
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
