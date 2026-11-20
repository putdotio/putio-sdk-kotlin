package io.putdotio.sdk.live

import io.putdotio.sdk.config.AppConfigUpdate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigGrantsRoutesLiveTest {
    @Test
    fun `config grants and routes read-only surfaces decode from the live API`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                sdk.appConfig.get()
                val grants = sdk.grants.list()
                val routes = sdk.routes.list()

                grants.firstOrNull()?.let { grant ->
                    assertTrue(grant.id > 0)
                    assertTrue(grant.name.isNotBlank())
                }
                routes.firstOrNull()?.let { route ->
                    assertTrue(route.name.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `config saves and deletes a disposable key`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val key = LiveSupport.recordFixture("config", LiveSupport.uniqueName("putio-kotlin-live"))
                val value = JsonPrimitive(LiveSupport.uniqueName("value"))
                assertFalse(key in sdk.appConfig.get().values)

                try {
                    sdk.appConfig.save(AppConfigUpdate(key = key, value = value))
                    assertEquals(value, sdk.appConfig.get()[key])
                } finally {
                    sdk.appConfig.delete(key)
                }
                assertFalse(key in sdk.appConfig.get().values)
            }
        }
    }

    @Test
    fun `oauth clients include the live token's session and app icons load`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val tokenId = assertNotNull(sdk.auth.validateToken().tokenId)
                val session = assertNotNull(sdk.grants.clients().find { it.id == tokenId })
                assertTrue(session.active)
                assertTrue(session.appId > 0)

                val grant = sdk.grants.list().firstOrNull { it.hasIcon == true }
                assumeTrue(grant != null, "No granted app with an icon for icon live coverage")
                val iconUrl = sdk.grants.buildIconUrl(assertNotNull(grant).id, LiveSupport.requireAccessToken())
                val connection = URI(iconUrl.value).toURL().openConnection() as HttpURLConnection
                try {
                    assertEquals(200, connection.responseCode)
                    assertTrue(connection.contentType.orEmpty().startsWith("image/"))
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}
