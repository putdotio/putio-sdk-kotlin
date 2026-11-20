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
                val clientsBefore =
                    sdk.grants
                        .clients()
                        .map { it.id }
                        .toSet()
                val code = sdk.auth.getCode().code
                assertNull(sdk.auth.checkCodeMatch(code))

                try {
                    val app = sdk.grants.linkDevice(code)
                    assertEquals(appId, app.id)

                    val token = assertNotNull(sdk.auth.checkCodeMatch(code))
                    val validation = sdk.auth.validateToken(token)
                    assertTrue(validation.result)
                    val linkedClientId = LiveSupport.recordFixture("client", assertNotNull(validation.tokenId))

                    val session = assertNotNull(sdk.grants.clients().find { it.id == linkedClientId })
                    assertEquals(appId, session.appId)

                    sdk.grants.revokeClient(linkedClientId)
                    assertTrue(sdk.grants.clients().none { it.id == linkedClientId })
                    assertFalse(runCatching { sdk.auth.validateToken(token).result }.getOrDefault(false))
                } finally {
                    sdk.grants
                        .clients()
                        .filter { it.appId == appId && it.id !in clientsBefore }
                        .forEach { sdk.grants.revokeClient(LiveSupport.recordFixture("client", it.id)) }
                }
                assertEquals(grantedBefore, sdk.isGranted(appId))
            }
        }
    }

    private suspend fun PutioClient.isGranted(appId: Long): Boolean = grants.list().any { it.id == appId }
}
