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
            val clientName = LiveSupport.uniqueName("putio-kotlin-device-link")
            LiveSupport.newAuthedClient(clientId = clientId, clientName = clientName).use { sdk ->
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
                    revokeLinkedClient(sdk, appId, clientName, code, token, linkedClientId)
                }
                assertEquals(grantedBefore, sdk.isGranted(appId))
            }
        }
    }

    @Test
    fun `logout with an explicit token revokes only the disposable linked token`() {
        runBlocking {
            val clientId = LiveSupport.requireClientId()
            val appId = clientId.toLong()
            val clientName = LiveSupport.uniqueName("putio-kotlin-device-link")
            LiveSupport.newAuthedClient(clientId = clientId, clientName = clientName).use { sdk ->
                val grantedBefore = sdk.isGranted(appId)
                var token: String? = null
                var linkedClientId: Long? = null
                val code = sdk.auth.getCode().code

                try {
                    sdk.grants.linkDevice(code)
                    val issued = assertNotNull(sdk.auth.checkCodeMatch(code))
                    token = issued
                    val validation = sdk.auth.validateToken(issued)
                    assertTrue(validation.result)
                    linkedClientId = LiveSupport.recordFixture("client", assertNotNull(validation.tokenId))

                    assertEquals("OK", sdk.auth.logout(token = issued).status)
                    assertFalse(runCatching { sdk.auth.validateToken(issued).result }.getOrDefault(false))
                    assertTrue(sdk.auth.validateToken().result)
                } finally {
                    revokeLinkedClient(sdk, appId, clientName, code, token, linkedClientId)
                }
                assertEquals(grantedBefore, sdk.isGranted(appId))
            }
        }
    }

    private suspend fun revokeLinkedClient(
        sdk: PutioClient,
        appId: Long,
        clientName: String,
        code: String,
        token: String?,
        linkedClientId: Long?,
    ) {
        val issued = token ?: runCatching { sdk.auth.checkCodeMatch(code) }.getOrNull()
        val resolvedId =
            linkedClientId
                ?: issued?.let { runCatching { sdk.auth.validateToken(it).tokenId }.getOrNull() }
        val currentClients = runCatching { sdk.grants.clients() }.getOrDefault(emptyList())
        val idsToRevoke =
            if (resolvedId != null) {
                currentClients.filter { it.id == resolvedId && it.appId == appId }.map { it.id }
            } else {
                currentClients.filter { it.appId == appId && it.clientName == clientName }.map { it.id }
            }
        idsToRevoke.forEach { id ->
            runCatching { sdk.grants.revokeClient(id) }
        }
    }

    private suspend fun PutioClient.isGranted(appId: Long): Boolean = grants.list().any { it.id == appId }
}
