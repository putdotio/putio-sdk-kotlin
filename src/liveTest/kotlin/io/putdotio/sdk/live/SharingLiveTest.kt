package io.putdotio.sdk.live

import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationErrorReason
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.sharing.CloneSharedFilesInput
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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

    @Test
    fun `cloning an owned folder is rejected and an unknown copy id is not found`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val folder = sdk.files.createFolder(name = LiveSupport.uniqueName("putio-kotlin-live"), parentId = 0)
                try {
                    // Nothing is shared with the owner of this folder, so put.io copies nothing. The
                    // destination is the disposable folder itself, which the cleanup removes either way.
                    val rejected =
                        assertFailsWith<PutioOperationException> {
                            sdk.sharing.cloneSharedFiles(CloneSharedFilesInput(ids = listOf(folder.id), parentId = folder.id))
                        }
                    assertEquals("cloneSharedFiles", rejected.operation)
                    assertEquals(400, assertIs<PutioApiException>(rejected.underlyingError).statusCode)

                    val missing = assertFailsWith<PutioOperationException> { sdk.sharing.getCloneInfo(1) }
                    assertEquals(PutioOperationErrorReason.ErrorType("SHARED_FILE_CLONE_NOT_FOUND"), missing.reason)
                } finally {
                    sdk.files.delete(fileIds = listOf(folder.id), skipTrash = true)
                }
            }
        }
    }
}
