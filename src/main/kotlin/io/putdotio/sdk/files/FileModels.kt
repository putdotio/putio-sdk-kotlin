package io.putdotio.sdk.files

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull

@Serializable
data class FileBreadcrumb(
    val id: Long,
    val name: String,
)

@Serializable(with = PutioFileType.Serializer::class)
@JvmInline
value class PutioFileType(
    val raw: String,
) {
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
value class PutioFolderType(
    val raw: String,
) {
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
) {
    init {
        require(status == "OK") { "File response status must be OK" }
    }
}

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
    val total: Int,
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
    val sortBy: String? = null,
    val mp4Status: Boolean = false,
    /** Include `video_metadata` (duration, codec, dimensions) on each listed child. */
    val videoMetadata: Boolean = false,
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
        if (sortBy != null) put("sort_by", sortBy)
        if (mp4Status) put("mp4_status", "1")
        if (videoMetadata) put("video_metadata", "1")
    }

data class FileDetailsQuery(
    val mp4Size: Boolean = true,
    val startFrom: Boolean = true,
    val streamUrl: Boolean = true,
    val mp4StreamUrl: Boolean = true,
    val mp4Status: Boolean = false,
)

internal fun FileDetailsQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (mp4Size) put("mp4_size", "1")
        if (mp4Status) put("mp4_status", "1")
        if (startFrom) put("start_from", "1")
        if (streamUrl) put("stream_url", "1")
        if (mp4StreamUrl) put("mp4_stream_url", "1")
    }

@Serializable
data class FileSubtitle(
    val format: String? = null,
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
internal data class FileDownloadUrlEnvelope(
    val url: String,
    val status: String,
) {
    init {
        require(status == "OK") { "Download URL response status must be OK" }
        require(url.toHttpUrlOrNull() != null) { "Download URL must be an absolute http(s) URL" }
    }
}

/**
 * Acknowledges a delete request without guaranteeing that every selected item was removed.
 * [cursor] identifies skipped items; it is not a deletion continuation to execute automatically.
 */
@Serializable
data class FileDeleteResult(
    val cursor: String? = null,
    val skipped: Int = 0,
    val status: String,
) {
    init {
        require(status == "OK") { "Delete result status must be OK" }
        require(skipped >= 0) { "Delete result skipped count must be nonnegative" }
        require(cursor == null || cursor.isNotBlank()) { "Delete result cursor must not be blank" }
    }
}

@Serializable
data class FileMoveError(
    @SerialName("error_type") val errorType: String,
    val id: Long,
    val name: String? = null,
    @SerialName("status_code") val statusCode: Int,
)

@Serializable
internal data class FileMoveEnvelope(
    val errors: List<FileMoveError>,
    val status: String,
) {
    init {
        require(status == "OK") { "Move result status must be OK" }
    }
}

@Serializable(with = NextFileType.Serializer::class)
@JvmInline
value class NextFileType(
    val raw: String,
) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val VIDEO = NextFileType("VIDEO")
        val AUDIO = NextFileType("AUDIO")

        private val knownValues = setOf(VIDEO, AUDIO)

        fun fromRaw(raw: String): NextFileType =
            when (raw) {
                VIDEO.raw -> VIDEO
                AUDIO.raw -> AUDIO
                else -> NextFileType(raw)
            }
    }

    object Serializer : RawStringValueSerializer<NextFileType>("NextFileType") {
        override fun fromRaw(raw: String): NextFileType = Companion.fromRaw(raw)

        override fun toRaw(value: NextFileType): String = value.raw
    }
}

@Serializable
data class NextFile(
    val id: Long,
    val name: String,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("file_type") val fileType: NextFileType? = null,
)

@Serializable
internal data class NextFileEnvelope(
    @SerialName("next_file") val nextFile: NextFile,
    val status: String,
)

@Serializable(with = FileMp4ConversionStatus.Serializer::class)
@JvmInline
value class FileMp4ConversionStatus(
    val raw: String,
) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val IN_QUEUE = FileMp4ConversionStatus("IN_QUEUE")
        val CONVERTING = FileMp4ConversionStatus("CONVERTING")
        val COMPLETED = FileMp4ConversionStatus("COMPLETED")
        val ERROR = FileMp4ConversionStatus("ERROR")
        val NOT_AVAILABLE = FileMp4ConversionStatus("NOT_AVAILABLE")

        private val knownValues = setOf(IN_QUEUE, CONVERTING, COMPLETED, ERROR, NOT_AVAILABLE)

        fun fromRaw(raw: String): FileMp4ConversionStatus =
            when (raw) {
                IN_QUEUE.raw -> IN_QUEUE
                CONVERTING.raw -> CONVERTING
                COMPLETED.raw -> COMPLETED
                ERROR.raw -> ERROR
                NOT_AVAILABLE.raw -> NOT_AVAILABLE
                else -> FileMp4ConversionStatus(raw)
            }
    }

    object Serializer : RawStringValueSerializer<FileMp4ConversionStatus>("FileMp4ConversionStatus") {
        override fun fromRaw(raw: String): FileMp4ConversionStatus = Companion.fromRaw(raw)

        override fun toRaw(value: FileMp4ConversionStatus): String = value.raw
    }
}

@Serializable
data class FileMp4Conversion(
    val id: Long? = null,
    @SerialName("percent_done") val percentDone: Double? = null,
    val size: Long? = null,
    val status: FileMp4ConversionStatus,
)

@Serializable
internal data class FileMp4ConversionEnvelope(
    val mp4: FileMp4Conversion,
    val status: String,
)

/**
 * A file sent to put.io's upload host. A `.torrent` or `.magnet` [fileName] starts a transfer
 * instead of saving a file; [requireTorrent] makes put.io reject anything else with
 * `NotTorrent` rather than store it. Without a [parentId], or with root (`0`), put.io saves a
 * started transfer to the account's default download folder.
 */
class FileUploadInput(
    val content: ByteArray,
    val fileName: String,
    val parentId: Long? = null,
    val requireTorrent: Boolean = false,
    val mediaType: String = "application/octet-stream",
) {
    init {
        require(fileName.isNotBlank()) { "Upload file name must not be blank" }
        require(parentId == null || parentId >= 0) { "Upload parent id must not be negative" }
        require(!requireTorrent || content.isNotEmpty()) { "Torrent upload content must not be empty" }
        require(mediaType.toMediaTypeOrNull() != null) { "Upload media type must be a valid media type" }
    }

    internal fun toFormMap(): Map<String, String> =
        buildMap {
            put("filename", fileName)
            parentId?.let { put("parent_id", it.toString()) }
            if (requireTorrent) put("torrent", "true")
        }

    override fun toString(): String =
        "FileUploadInput(fileName=$fileName, size=${content.size}, parentId=$parentId, requireTorrent=$requireTorrent)"
}

sealed interface FileUploadResult {
    data class File(
        val file: PutioFile,
    ) : FileUploadResult

    data class Transfer(
        val transfer: io.putdotio.sdk.transfers.Transfer,
    ) : FileUploadResult
}

@Serializable
internal data class FileUploadEnvelope(
    val file: PutioFile? = null,
    val transfer: io.putdotio.sdk.transfers.Transfer? = null,
    val status: String,
) {
    init {
        require(status == "OK") { "Upload response status must be OK" }
        require((file == null) != (transfer == null)) { "Upload response must contain exactly one of file or transfer" }
    }

    fun toResult(): FileUploadResult = file?.let(FileUploadResult::File) ?: FileUploadResult.Transfer(requireNotNull(transfer))
}
