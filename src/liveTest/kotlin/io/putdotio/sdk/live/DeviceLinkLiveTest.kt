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
                var token: String? = null
                var linkedClientId: Long? = null
                val code = sdk.auth.getCode().code
                assertNull(sdk.auth.checkCodeMatch(code))

                try {
                    val app = sdk.grants.linkDevice(code)
                    token = sdk.auth.checkCodeMatch(code)
                    assertEquals(appId, app.id)

                    val issued = assertNotNull(token)
                    val validation = sdk.auth.validateToken(issued)
                    assertTrue(validation.result)
                    val linkedId = LiveSupport.recordFixture("client", assertNotNull(validation.tokenId))
                    val session = assertNotNull(sdk.grants.clients().find { it.id == linkedId })
                    assertEquals(appId, session.appId)
                    linkedClientId = linkedId

                    sdk.grants.revokeClient(linkedId)
                    assertTrue(sdk.grants.clients().none { it.id == linkedId })
                    assertFalse(runCatching { sdk.auth.validateToken(issued).result }.getOrDefault(false))
                } finally {
                    val issued = token ?: runCatching { sdk.auth.checkCodeMatch(code) }.getOrNull()
                    val linkedId =
                        linkedClientId
                            ?: issued?.let { runCatching { sdk.auth.validateToken(it).tokenId }.getOrNull() }
                    linkedId
                        ?.takeIf { id -> sdk.grants.clients().any { it.id == id && it.appId == appId } }
                        ?.let { sdk.grants.revokeClient(it) }
                    if (!grantedBefore && sdk.isGranted(appId)) sdk.grants.revoke(appId)
                }
                assertEquals(grantedBefore, sdk.isGranted(appId))
            }
        }
    }

    private suspend fun PutioClient.isGranted(appId: Long): Boolean = grants.list().any { it.id == appId }
}
