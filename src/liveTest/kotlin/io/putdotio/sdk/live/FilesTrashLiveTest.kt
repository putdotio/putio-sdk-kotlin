package io.putdotio.sdk.live

import io.putdotio.sdk.account.AccountSettingsPatch
import io.putdotio.sdk.trash.TrashBulkInput
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FilesTrashLiveTest {
    @Test
    fun `files search returns stable live results`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val search = sdk.files.search(
                    query = io.putdotio.sdk.files.FilesSearchQuery(
                        keyword = "mp4",
                        perPage = 5,
                    ),
                )

                assertTrue(search.total >= search.files.size)
                assertTrue(search.files.isNotEmpty())
            }
        }
    }

    @Test
    fun `files and trash flows work with disposable data`() {
        runBlocking {
            val folderName = LiveSupport.uniqueName("putio-kotlin-live")

            LiveSupport.newAuthedClient().use { sdk ->
                val settings = sdk.account.getSettings()
                if (!settings.trashEnabled) {
                    sdk.account.saveSettings(AccountSettingsPatch(trashEnabled = true))
                }

                try {
                    val created = sdk.files.createFolder(name = folderName, parentId = 0)
                    var isInTrash = false
                    assertEquals(folderName, created.name)

                    try {
                        val listing = sdk.files.list(parentId = 0)
                        assertTrue(listing.files.any { it.id == created.id })

                        val deleteResult = sdk.files.delete(fileIds = listOf(created.id))
                        isInTrash = true
                        assertTrue(deleteResult.status == "OK")

                        val trashed = sdk.trash.list()
                        val trashedFile = trashed.files.find { it.id == created.id }
                        assertNotNull(trashedFile)

                        sdk.trash.restore(TrashBulkInput(ids = listOf(created.id)))
                        isInTrash = false
                        val restored = sdk.files.get(created.id)
                        assertEquals(created.id, restored.id)
                    } finally {
                        if (isInTrash) {
                            sdk.trash.delete(TrashBulkInput(ids = listOf(created.id)))
                        } else {
                            sdk.files.delete(fileIds = listOf(created.id), skipTrash = true)
                        }
                    }
                } finally {
                    if (!settings.trashEnabled) {
                        sdk.account.saveSettings(AccountSettingsPatch(trashEnabled = false))
                    }
                }
            }
        }
    }
}
