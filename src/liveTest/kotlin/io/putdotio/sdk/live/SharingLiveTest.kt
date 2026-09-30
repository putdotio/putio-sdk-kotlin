package io.putdotio.sdk.live

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class SharingLiveTest {
    @Test
    fun `shared files and public shares decode from the live API`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                sdk.sharing.listSharedFiles().forEach { shared ->
                    assertTrue(shared.file.id > 0)
                }
                sdk.sharing.publicShares.list().forEach { share ->
                    assertTrue(share.id > 0)
                    assertTrue(share.token.value.isNotBlank())
                }
            }
        }
    }
}
