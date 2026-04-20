package io.putdotio.sdk.files

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileBreadcrumb(
    val id: Long,
    val name: String,
)

@Serializable
enum class PutioFileType {
    FOLDER,
    FILE,
    AUDIO,
    VIDEO,
    IMAGE,
    ARCHIVE,
    PDF,
    TEXT,
    SWF,
}

@Serializable
enum class PutioFolderType {
    REGULAR,
    SHARED_ROOT,
    SHARED_FRIEND,
}

@Serializable
data class PutioVideoMetadata(
    val height: Int? = null,
    val width: Int? = null,
    val codec: String? = null,
    val duration: Double? = null,
    @SerialName("aspect_ratio") val aspectRatio: Double? = null,
)

@Serializable
data class PutioFile(
    val id: Long,
    val name: String,
    val icon: String? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    val size: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("file_type") val fileType: PutioFileType,
    @SerialName("folder_type") val folderType: PutioFolderType = PutioFolderType.REGULAR,
    @SerialName("is_shared") val isShared: Boolean = false,
    @SerialName("is_mp4_available") val isMp4Available: Boolean = false,
    @SerialName("need_convert") val needConvert: Boolean = false,
    @SerialName("sort_by") val sortBy: String? = null,
    @SerialName("start_from") val startFrom: Double? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("mp4_stream_url") val mp4StreamUrl: String? = null,
    @SerialName("mp4_size") val mp4Size: Long? = null,
    val screenshot: String? = null,
    @SerialName("video_metadata") val videoMetadata: PutioVideoMetadata? = null,
)

@Serializable
internal data class FileEnvelope(
    val file: PutioFile,
    val status: String,
)

@Serializable
data class FilesListResponse(
    val parent: PutioFile? = null,
    val files: List<PutioFile> = emptyList(),
    val cursor: String? = null,
    val total: Int? = null,
    val status: String,
)

data class FilesListQuery(
    val perPage: Int? = null,
    val total: Boolean = false,
    val hidden: Boolean = false,
    val noCursor: Boolean = false,
    val contentType: String? = null,
    val fileType: String? = null,
)

internal fun FilesListQuery.toQueryMap(parentId: Long): Map<String, String> =
    buildMap {
        put("parent_id", parentId.toString())
        put("mp4_status_parent", "1")
        put("stream_url_parent", "1")
        put("mp4_stream_url_parent", "1")
        put("video_metadata_parent", "1")
        if (perPage != null) put("per_page", perPage.toString())
        if (total) put("total", "1")
        if (hidden) put("hidden", "1")
        if (noCursor) put("no_cursor", "1")
        if (contentType != null) put("content_type", contentType)
        if (fileType != null) put("file_type", fileType)
    }

data class FileDetailsQuery(
    val mp4Size: Boolean = true,
    val startFrom: Boolean = true,
    val streamUrl: Boolean = true,
    val mp4StreamUrl: Boolean = true,
)

internal fun FileDetailsQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (mp4Size) put("mp4_size", "1")
        if (startFrom) put("start_from", "1")
        if (streamUrl) put("stream_url", "1")
        if (mp4StreamUrl) put("mp4_stream_url", "1")
    }

