package io.putdotio.sdk.live

import io.putdotio.sdk.PutioClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLinkLiveTest {
    @Test
    fun `a linked device code issues a token that revoking its client invalidates`() {
        runBlocking {
            val clientId = LiveSupport.requireClientId()
            val appId = clientId.toLong()
            LiveSupport.newAuthedClient(clientId = clientId).use { sdk ->
                val grantedBefore = sdk.isGranted(appId)
                var linkedClientId: Long? = null
                val code = sdk.auth.getCode().code
                assertNull(sdk.auth.checkCodeMatch(code))

                try {
                    val app = sdk.grants.linkDevice(code)
                    assertEquals(appId, app.id)

                    val token = assertNotNull(sdk.auth.checkCodeMatch(code))
                    val validation = sdk.auth.validateToken(token)
                    assertTrue(validation.result)
                    val linkedId = LiveSupport.recordFixture("client", assertNotNull(validation.tokenId))
                    linkedClientId = linkedId

                    val session = assertNotNull(sdk.grants.clients().find { it.id == linkedId })
                    assertEquals(appId, session.appId)

                    sdk.grants.revokeClient(linkedId)
                    assertTrue(sdk.grants.clients().none { it.id == linkedId })
                    assertFalse(runCatching { sdk.auth.validateToken(token).result }.getOrDefault(false))
                } finally {
                    linkedClientId
                        ?.takeIf { id -> sdk.grants.clients().any { it.id == id } }
                        ?.let { sdk.grants.revokeClient(it) }
                }
                assertEquals(grantedBefore, sdk.isGranted(appId))
            }
        }
    }

    private suspend fun PutioClient.isGranted(appId: Long): Boolean = grants.list().any { it.id == appId }
}
