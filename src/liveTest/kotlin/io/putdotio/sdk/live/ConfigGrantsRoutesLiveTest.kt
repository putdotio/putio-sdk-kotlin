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
                val request = sdk.grants.buildIconRequest(assertNotNull(grant).id)
                assertFalse(request.url.contains("oauth_token"))
                assertEquals(200, iconStatus(request.url, request.headers))
            }
        }
    }

    @Test
    @org.junit.jupiter.api.Tag("error-path")
    fun `app icon rejects unauthenticated requests with 401`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val grant = sdk.grants.list().firstOrNull { it.hasIcon == true }
                assumeTrue(grant != null, "No granted app with an icon for icon live coverage")
                val request = sdk.grants.buildIconRequest(assertNotNull(grant).id)
                assertEquals(401, iconStatus(request.url, emptyMap()))
            }
        }
    }

    private fun iconStatus(
        url: String,
        headers: Map<String, String>,
    ): Int {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = ICON_TIMEOUT_MS
        connection.readTimeout = ICON_TIMEOUT_MS
        headers.forEach(connection::setRequestProperty)
        return try {
            connection.responseCode.also { status ->
                if (status == 200) assertTrue(connection.contentType.orEmpty().startsWith("image/"))
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ICON_TIMEOUT_MS = 30_000
    }
}
