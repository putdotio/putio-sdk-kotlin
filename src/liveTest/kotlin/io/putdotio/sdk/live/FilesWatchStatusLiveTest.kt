package io.putdotio.sdk.live

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.files.FileUploadInput
import io.putdotio.sdk.files.FileUploadResult
import io.putdotio.sdk.files.FilesListQuery
import io.putdotio.sdk.files.FilesWatchStatusInput
import io.putdotio.sdk.files.PutioFileType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class FilesWatchStatusLiveTest {
    @Test
    fun `watch status marks uploaded videos by ids and by cursor`() {
        runBlocking {
            LiveSupport.newAuthedClient().use { sdk ->
                assumeTrue(sdk.account.getSettings().useStartFrom, "Watch status needs resume positions on")
                val folder = sdk.files.createFolder(name = LiveSupport.uniqueName("putio-kotlin-live"), parentId = 0)
                println("watch-status live fixture folder ${folder.id} ${folder.name}")
                try {
                    val first = uploadVideo(sdk, folder.id, "First sample.mp4")
                    val second = uploadVideo(sdk, folder.id, "Second sample.mp4")

                    sdk.files.setWatchStatus(FilesWatchStatusInput(watched = true, ids = listOf(first)))
                    assertEquals(1.0, sdk.files.getStartFrom(first))
                    assertEquals(0.0, sdk.files.getStartFrom(second))

                    sdk.files.setWatchStatus(FilesWatchStatusInput(watched = false, ids = listOf(first)))
                    assertEquals(0.0, sdk.files.getStartFrom(first))

                    sdk.files.setStartFrom(fileId = second, time = 42.0)
                    val cursor = assertNotNull(sdk.files.list(folder.id, FilesListQuery(perPage = 1)).cursor)
                    sdk.files.setWatchStatus(FilesWatchStatusInput(watched = true, cursor = cursor))
                    assertEquals(1.0, sdk.files.getStartFrom(first))
                    assertEquals(42.0, sdk.files.getStartFrom(second))

                    sdk.files.setWatchStatus(
                        FilesWatchStatusInput(watched = false, cursor = cursor, excludeIds = listOf(first)),
                    )
                    assertEquals(1.0, sdk.files.getStartFrom(first))
                    assertEquals(0.0, sdk.files.getStartFrom(second))
                } finally {
                    sdk.files.delete(fileIds = listOf(folder.id), skipTrash = true)
                }
            }
        }
    }

    private suspend fun uploadVideo(
        sdk: PutioClient,
        parentId: Long,
        fileName: String,
    ): Long {
        val sample = assertNotNull(javaClass.getResourceAsStream("/watch-status-sample.mp4")).use { it.readBytes() }
        val content = sample + fileName.toByteArray()
        val file =
            assertIs<FileUploadResult.File>(
                sdk.files.upload(FileUploadInput(content = content, fileName = fileName, parentId = parentId)),
            ).file
        assertEquals(PutioFileType.VIDEO, file.fileType)
        println("watch-status live fixture file ${file.id} $fileName")
        return file.id
    }
}
