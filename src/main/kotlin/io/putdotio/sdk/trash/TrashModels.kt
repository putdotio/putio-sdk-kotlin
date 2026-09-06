package io.putdotio.sdk.trash

import io.putdotio.sdk.files.PutioFileType
import io.putdotio.sdk.files.PutioFolderType
import io.putdotio.sdk.files.PutioVideoMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrashFile(
    val id: Long,
    val name: String,
    val icon: String? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    val size: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("file_type") val fileType: PutioFileType,
    @SerialName("folder_type") val folderType: PutioFolderType = PutioFolderType.REGULAR,
    @SerialName("deleted_at") val deletedAt: String,
    @SerialName("expiration_date") val expirationDate: String,
    @SerialName("video_metadata") val videoMetadata: PutioVideoMetadata? = null,
)

/**
 * [total] and [trashSize] describe the initial list. Continuation responses can omit
 * these aggregates; their compatibility defaults do not replace initial-list totals.
 */
@Serializable
data class TrashListResponse(
    val cursor: String? = null,
    val files: List<TrashFile> = emptyList(),
    val total: Int? = null,
    @SerialName("trash_size") val trashSize: Long = 0,
    val status: String,
)

@Serializable
internal data class TrashListEnvelope(
    val cursor: String? = null,
    val files: List<TrashFile>,
    val total: Int,
    @SerialName("trash_size") val trashSize: Long,
    val status: String,
) {
    init {
        require(status == "OK") { "Trash list status must be OK" }
        require(total >= 0) { "Trash total must be nonnegative" }
        require(trashSize >= 0) { "Trash size must be nonnegative" }
    }

    fun toResponse(): TrashListResponse = TrashListResponse(cursor, files, total, trashSize, status)
}

@Serializable
internal data class TrashContinueEnvelope(
    val cursor: String? = null,
    val files: List<TrashFile>,
    val total: Int? = null,
    @SerialName("trash_size") val trashSize: Long = 0,
    val status: String,
) {
    init {
        require(status == "OK") { "Trash continuation status must be OK" }
        require(total == null || total >= 0) { "Trash total must be nonnegative" }
        require(trashSize >= 0) { "Trash size must be nonnegative" }
    }

    fun toResponse(): TrashListResponse = TrashListResponse(cursor, files, total, trashSize, status)
}

data class TrashListQuery(
    val perPage: Int? = null,
)

data class TrashContinueQuery(
    val perPage: Int? = null,
)

internal fun TrashListQuery.toQueryMap(): Map<String, String> = toPerPageQueryMap(perPage)

internal fun TrashContinueQuery.toQueryMap(): Map<String, String> = toPerPageQueryMap(perPage)

private fun toPerPageQueryMap(perPage: Int?): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
    }

data class TrashBulkInput(
    val ids: List<Long> = emptyList(),
    val cursor: String? = null,
) {
    init {
        require(ids.isNotEmpty() || !cursor.isNullOrBlank()) {
            "TrashBulkInput requires either ids or cursor"
        }
    }
}

internal fun TrashBulkInput.toFormMap(): Map<String, String> =
    cursor?.takeIf { it.isNotBlank() }?.let { mapOf("cursor" to it) }
        ?: mapOf("file_ids" to ids.joinToString(","))
