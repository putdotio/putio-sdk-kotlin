package io.putdotio.sdk.live

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLinkLiveTest {
    @Test
    fun `a linked device code issues a token that revoking its client invalidates`() {
        runBlocking {
            val clientId = LiveSupport.requireClientId()
            LiveSupport.newAuthedClient(clientId = clientId).use { sdk ->
                val grantedBefore =
                    sdk.grants
                        .list()
                        .map { it.id }
                        .toSet()
                val code = sdk.auth.getCode().code
                assertNull(sdk.auth.checkCodeMatch(code))

                var linkedAppId: Long? = null
                var linkedClientId: Long? = null
                try {
                    val app = sdk.grants.linkDevice(code)
                    linkedAppId = LiveSupport.recordFixture("grant", app.id)
                    assertEquals(clientId.toLong(), app.id)

                    val token = assertNotNull(sdk.auth.checkCodeMatch(code))
                    val validation = sdk.auth.validateToken(token)
                    assertTrue(validation.result)
                    linkedClientId = LiveSupport.recordFixture("client", assertNotNull(validation.tokenId))

                    val session = assertNotNull(sdk.grants.clients().find { it.id == linkedClientId })
                    assertEquals(app.id, session.appId)

                    sdk.grants.revokeClient(linkedClientId)
                    linkedClientId = null
                    assertTrue(sdk.grants.clients().none { it.id == validation.tokenId && it.active })
                } finally {
                    linkedClientId?.let { sdk.grants.revokeClient(it) }
                    linkedAppId?.takeIf { it !in grantedBefore }?.let { sdk.grants.revoke(it) }
                }
                assertEquals(
                    grantedBefore,
                    sdk.grants
                        .list()
                        .map { it.id }
                        .toSet(),
                )
            }
        }
    }
}
