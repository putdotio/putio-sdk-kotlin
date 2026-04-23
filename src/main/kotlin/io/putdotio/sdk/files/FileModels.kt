package io.putdotio.sdk.files

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileBreadcrumb(
    val id: Long,
    val name: String,
)

@Serializable(with = PutioFileType.Serializer::class)
@JvmInline
value class PutioFileType(val raw: String) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val FOLDER = PutioFileType("FOLDER")
        val FILE = PutioFileType("FILE")
        val AUDIO = PutioFileType("AUDIO")
        val VIDEO = PutioFileType("VIDEO")
        val IMAGE = PutioFileType("IMAGE")
        val ARCHIVE = PutioFileType("ARCHIVE")
        val PDF = PutioFileType("PDF")
        val TEXT = PutioFileType("TEXT")
        val SWF = PutioFileType("SWF")

        private val knownValues = setOf(FOLDER, FILE, AUDIO, VIDEO, IMAGE, ARCHIVE, PDF, TEXT, SWF)

        fun fromRaw(raw: String): PutioFileType =
            when (raw) {
                FOLDER.raw -> FOLDER
                FILE.raw -> FILE
                AUDIO.raw -> AUDIO
                VIDEO.raw -> VIDEO
                IMAGE.raw -> IMAGE
                ARCHIVE.raw -> ARCHIVE
                PDF.raw -> PDF
                TEXT.raw -> TEXT
                SWF.raw -> SWF
                else -> PutioFileType(raw)
            }
    }

    object Serializer : RawStringValueSerializer<PutioFileType>("PutioFileType") {
        override fun fromRaw(raw: String): PutioFileType = Companion.fromRaw(raw)

        override fun toRaw(value: PutioFileType): String = value.raw
    }
}

@Serializable(with = PutioFolderType.Serializer::class)
@JvmInline
value class PutioFolderType(val raw: String) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val REGULAR = PutioFolderType("REGULAR")
        val SHARED_ROOT = PutioFolderType("SHARED_ROOT")
        val SHARED_FRIEND = PutioFolderType("SHARED_FRIEND")

        private val knownValues = setOf(REGULAR, SHARED_ROOT, SHARED_FRIEND)

        fun fromRaw(raw: String): PutioFolderType =
            when (raw) {
                REGULAR.raw -> REGULAR
                SHARED_ROOT.raw -> SHARED_ROOT
                SHARED_FRIEND.raw -> SHARED_FRIEND
                else -> PutioFolderType(raw)
            }
    }

    object Serializer : RawStringValueSerializer<PutioFolderType>("PutioFolderType") {
        override fun fromRaw(raw: String): PutioFolderType = Companion.fromRaw(raw)

        override fun toRaw(value: PutioFolderType): String = value.raw
    }
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

@Serializable
data class FileSearchResponse(
    val cursor: String? = null,
    val files: List<PutioFile> = emptyList(),
    val total: Int = 0,
    val status: String,
)

data class FilesSearchQuery(
    val keyword: String,
    val perPage: Int? = null,
    val type: List<PutioFileType> = emptyList(),
)

internal fun FilesSearchQuery.toQueryMap(): Map<String, String> =
    buildMap {
        put("query", keyword)
        if (perPage != null) put("per_page", perPage.toString())
        if (type.isNotEmpty()) put("type", type.joinToString(",") { it.raw })
    }

data class FilesContinueQuery(
    val perPage: Int? = null,
)

internal fun FilesContinueQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
    }

data class FilesListQuery(
    val perPage: Int? = null,
    val total: Boolean = false,
    val hidden: Boolean = false,
    val noCursor: Boolean = false,
    val contentType: String? = null,
    val fileType: PutioFileType? = null,
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
        if (fileType != null) put("file_type", fileType.raw)
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

@Serializable
data class FileSubtitle(
    val key: String,
    val language: String,
    @SerialName("language_code") val languageCode: String,
    val name: String,
    val source: String,
    val url: String,
)

@Serializable
data class FileSubtitlesResponse(
    @SerialName("default") val defaultKey: String? = null,
    val subtitles: List<FileSubtitle> = emptyList(),
    val status: String,
)

@Serializable
internal data class FileStartFromResponse(
    @SerialName("start_from") val startFrom: Double,
    val status: String,
)

@Serializable
data class FileDeleteResult(
    val cursor: String? = null,
    val skipped: Int = 0,
    val status: String,
)

@Serializable
data class FileMoveError(
    @SerialName("error_type") val errorType: String,
    val id: Long,
    val name: String? = null,
    @SerialName("status_code") val statusCode: Int,
)

@Serializable
internal data class FileMoveEnvelope(
    val errors: List<FileMoveError> = emptyList(),
    val status: String,
)
