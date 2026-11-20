package io.putdotio.sdk.live

import io.putdotio.sdk.sharing.SharedWith
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FilesOrganizeLiveTest {
    @Test
    fun `rename move sort and shared-with work on disposable folders`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val root = sdk.files.createFolder(name = LiveSupport.uniqueName("putio-kotlin-live"), parentId = 0)
                LiveSupport.recordFixture("folder", root.id)
                try {
                    val source = sdk.files.createFolder(name = LiveSupport.uniqueName("source"), parentId = root.id)
                    val target = sdk.files.createFolder(name = LiveSupport.uniqueName("target"), parentId = root.id)
                    val renamed = LiveSupport.uniqueName("renamed")

                    sdk.files.rename(fileId = source.id, name = renamed)
                    assertEquals(renamed, sdk.files.get(source.id).name)

                    assertEquals(emptyList(), sdk.files.move(fileIds = listOf(source.id), parentId = target.id))
                    assertEquals(target.id, sdk.files.get(source.id).parentId)

                    sdk.files.setSortBy(fileId = target.id, sortBy = "NAME_DESC")
                    assertEquals("NAME_DESC", assertNotNull(sdk.files.list(parentId = target.id).parent).sortBy)

                    val sharedWith = assertIs<SharedWith.Friends>(sdk.sharing.getSharedWith(root.id))
                    assertTrue(sharedWith.shares.isEmpty())
                } finally {
                    sdk.files.delete(fileIds = listOf(root.id), skipTrash = true)
                }
            }
        }
    }
}
