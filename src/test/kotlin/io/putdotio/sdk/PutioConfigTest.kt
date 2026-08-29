package io.putdotio.sdk

import okhttp3.OkHttpClient
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PutioConfigTest {
    @Test
    fun `access token keeps the public data class API with volatile visibility`() {
        val config = PutioConfig(accessToken = "initial-token")
        val accessTokenField = PutioConfig::class.java.getDeclaredField("accessToken")

        assertTrue(Modifier.isVolatile(accessTokenField.modifiers))
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
}
