package io.putdotio.sdk.live

import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.files.FileUploadInput
import io.putdotio.sdk.files.FileUploadResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FilesUploadLiveTest {
    @Test
    fun `upload saves a file in a disposable folder and requireTorrent rejects non-torrents`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                val folder = sdk.files.createFolder(name = LiveSupport.uniqueName("putio-kotlin-live"), parentId = 0)
                try {
                    val content = "putio-sdk-kotlin live upload".toByteArray()
                    val saved =
                        sdk.files.upload(
                            FileUploadInput(content = content, fileName = "Sample notes.txt", parentId = folder.id),
                        )
                    val file = assertIs<FileUploadResult.File>(saved).file
                    assertEquals("Sample notes.txt", file.name)
                    assertEquals(folder.id, file.parentId)
                    assertEquals(content.size.toLong(), file.size)

                    val rejected =
                        assertFailsWith<PutioOperationException> {
                            sdk.files.upload(
                                FileUploadInput(
                                    content = content,
                                    fileName = "Sample other.txt",
                                    parentId = folder.id,
                                    requireTorrent = true,
                                ),
                            )
                        }
                    assertEquals("NotTorrent", assertIs<PutioApiException>(rejected.underlyingError).errorType)
                    assertEquals(
                        listOf(file.id),
                        sdk.files
                            .list(parentId = folder.id)
                            .files
                            .map { it.id },
                    )
                } finally {
                    sdk.files.delete(fileIds = listOf(folder.id), skipTrash = true)
                }
            }
        }
    }
}
