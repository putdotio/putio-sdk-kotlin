package io.putdotio.sdk.live

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigGrantsRoutesLiveTest {
    @Test
    fun `config grants and routes read-only surfaces decode from the live API`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val config = sdk.userConfig.get()
                val grants = sdk.grants.list()
                val routes = sdk.routes.list()

                assertTrue(config.chromecastPlaybackType.raw.isNotBlank())
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
}
