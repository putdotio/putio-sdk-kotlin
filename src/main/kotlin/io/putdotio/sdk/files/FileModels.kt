package io.putdotio.sdk.files

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull

@Serializable
public data class FileBreadcrumb(
    public val id: Long,
    public val name: String,
)

@Serializable(with = PutioFileType.Serializer::class)
@JvmInline
public value class PutioFileType(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = this in knownValues

    public override fun toString(): String = raw

    public companion object {
        public val FOLDER: PutioFileType = PutioFileType("FOLDER")
        public val FILE: PutioFileType = PutioFileType("FILE")
        public val AUDIO: PutioFileType = PutioFileType("AUDIO")
        public val VIDEO: PutioFileType = PutioFileType("VIDEO")
        public val IMAGE: PutioFileType = PutioFileType("IMAGE")
        public val ARCHIVE: PutioFileType = PutioFileType("ARCHIVE")
        public val PDF: PutioFileType = PutioFileType("PDF")
        public val TEXT: PutioFileType = PutioFileType("TEXT")
        public val SWF: PutioFileType = PutioFileType("SWF")

        private val knownValues = setOf(FOLDER, FILE, AUDIO, VIDEO, IMAGE, ARCHIVE, PDF, TEXT, SWF)

        public fun fromRaw(raw: String): PutioFileType =
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

    public object Serializer : RawStringValueSerializer<PutioFileType>("PutioFileType") {
        override fun fromRaw(raw: String): PutioFileType = Companion.fromRaw(raw)

        override fun toRaw(value: PutioFileType): String = value.raw
    }
}

@Serializable(with = PutioFolderType.Serializer::class)
@JvmInline
public value class PutioFolderType(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = this in knownValues

    public override fun toString(): String = raw

    public companion object {
        public val REGULAR: PutioFolderType = PutioFolderType("REGULAR")
        public val SHARED_ROOT: PutioFolderType = PutioFolderType("SHARED_ROOT")
        public val SHARED_FRIEND: PutioFolderType = PutioFolderType("SHARED_FRIEND")

        private val knownValues = setOf(REGULAR, SHARED_ROOT, SHARED_FRIEND)

        public fun fromRaw(raw: String): PutioFolderType =
            when (raw) {
                REGULAR.raw -> REGULAR
                SHARED_ROOT.raw -> SHARED_ROOT
                SHARED_FRIEND.raw -> SHARED_FRIEND
                else -> PutioFolderType(raw)
            }
    }

    public object Serializer : RawStringValueSerializer<PutioFolderType>("PutioFolderType") {
        override fun fromRaw(raw: String): PutioFolderType = Companion.fromRaw(raw)

        override fun toRaw(value: PutioFolderType): String = value.raw
    }
}

@Serializable
public data class PutioVideoMetadata(
    public val height: Int? = null,
    public val width: Int? = null,
    public val codec: String? = null,
    public val duration: Double? = null,
    @SerialName("aspect_ratio") public val aspectRatio: Double? = null,
)

@Serializable(with = PutioPlaybackIssue.Serializer::class)
@JvmInline
public value class PutioPlaybackIssue(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = raw in knownValues

    public override fun toString(): String = raw

    public companion object {
        public val MALFORMED_SEI: PutioPlaybackIssue = PutioPlaybackIssue("malformed_sei")
        public val MALFORMED_PARAMETER_SETS: PutioPlaybackIssue = PutioPlaybackIssue("malformed_parameter_sets")
        public val SPARSE_IDR: PutioPlaybackIssue = PutioPlaybackIssue("sparse_idr")
        public val INTERLACED_VIDEO: PutioPlaybackIssue = PutioPlaybackIssue("interlaced_video")
        public val EXCESSIVE_FRAME_RATE: PutioPlaybackIssue = PutioPlaybackIssue("excessive_frame_rate")
        public val OPEN_GOP: PutioPlaybackIssue = PutioPlaybackIssue("open_gop")
        public val MOVIE_FRAGMENTS: PutioPlaybackIssue = PutioPlaybackIssue("movie_fragments")

        private val knownValues =
            listOf(
                MALFORMED_SEI,
                MALFORMED_PARAMETER_SETS,
                SPARSE_IDR,
                INTERLACED_VIDEO,
                EXCESSIVE_FRAME_RATE,
                OPEN_GOP,
                MOVIE_FRAGMENTS,
            ).associateBy { it.raw }

        public fun fromRaw(raw: String): PutioPlaybackIssue = knownValues[raw] ?: PutioPlaybackIssue(raw)
    }

    public object Serializer : RawStringValueSerializer<PutioPlaybackIssue>("PutioPlaybackIssue") {
        override fun fromRaw(raw: String): PutioPlaybackIssue = Companion.fromRaw(raw)

        override fun toRaw(value: PutioPlaybackIssue): String = value.raw
    }
}

@Serializable
public data class PutioMediaFormat(
    public val name: String? = null,
    public val duration: Double? = null,
    @SerialName("bit_rate") public val bitRate: Long? = null,
)

/** One ffprobe stream. [level] is `-99` when FFmpeg does not know the codec level. */
@Serializable
public data class PutioMediaStream(
    @SerialName("codec_type") public val codecType: String? = null,
    @SerialName("codec_name") public val codecName: String? = null,
    @SerialName("rfc6381_codec") public val rfc6381Codec: String? = null,
    public val width: Int? = null,
    public val height: Int? = null,
    public val profile: String? = null,
    public val level: Int? = null,
    public val channels: Int? = null,
    @SerialName("bit_depth") public val bitDepth: Int? = null,
    @SerialName("hdr") public val isHdr: Boolean? = null,
    @SerialName("dolby_vision_profile") public val dolbyVisionProfile: Int? = null,
    @SerialName("default") public val isDefault: Boolean? = null,
)

@Serializable
public data class PutioMediaInfo(
    @SerialName("mime_type") public val mimeType: String? = null,
    public val format: PutioMediaFormat? = null,
    public val streams: List<PutioMediaStream>? = null,
    @SerialName("playback_issues") public val playbackIssues: List<PutioPlaybackIssue>? = null,
)

@Serializable
public data class PutioFile(
    public val id: Long,
    public val name: String,
    public val icon: String? = null,
    @SerialName("parent_id") public val parentId: Long? = null,
    public val size: Long,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("updated_at") public val updatedAt: String? = null,
    @SerialName("file_type") public val fileType: PutioFileType,
    @SerialName("folder_type") public val folderType: PutioFolderType = PutioFolderType.REGULAR,
    @SerialName("is_shared") public val isShared: Boolean = false,
    @SerialName("is_mp4_available") public val isMp4Available: Boolean = false,
    @SerialName("need_convert") public val needConvert: Boolean = false,
    @SerialName("sort_by") public val sortBy: String? = null,
    @SerialName("start_from") public val startFrom: Double? = null,
    @SerialName("stream_url") public val streamUrl: String? = null,
    @SerialName("mp4_stream_url") public val mp4StreamUrl: String? = null,
    @SerialName("mp4_size") public val mp4Size: Long? = null,
    public val screenshot: String? = null,
    @SerialName("video_metadata") public val videoMetadata: PutioVideoMetadata? = null,
    @SerialName("media_info") public val mediaInfo: PutioMediaInfo? = null,
    /** The MIME type put.io detected, such as `video/mp4`; folders report `application/x-directory`. */
    @SerialName("content_type") public val contentType: String? = null,
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
public data class FilesListResponse(
    public val parent: PutioFile? = null,
    public val files: List<PutioFile> = emptyList(),
    public val cursor: String? = null,
    public val total: Int? = null,
    public val status: String,
)

@Serializable
public data class FileSearchResponse(
    public val cursor: String? = null,
    public val files: List<PutioFile> = emptyList(),
    public val total: Int,
    public val status: String,
)

/**
 * Files to mark watched or unwatched with [FilesApi.setWatchStatus]: [ids], or every file a list or
 * search [cursor] covers, first page included, minus [excludeIds]. put.io skips folders and other
 * non-media files.
 */
public data class FilesWatchStatusInput(
    public val watched: Boolean,
    public val ids: List<Long> = emptyList(),
    public val cursor: String? = null,
    public val excludeIds: List<Long> = emptyList(),
) {
    init {
        require(ids.isNotEmpty() || !cursor.isNullOrBlank()) { "FilesWatchStatusInput requires either ids or cursor" }
        require(ids.all { it > 0 } && excludeIds.all { it > 0 }) { "Watch status file ids must be positive" }
    }
}

internal fun FilesWatchStatusInput.toFormMap(): Map<String, String> =
    buildMap {
        cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        if (excludeIds.isNotEmpty()) put("exclude_ids", excludeIds.joinToString(","))
        if (ids.isNotEmpty()) put("file_ids", ids.joinToString(","))
        put("watched", if (watched) "true" else "")
    }

public data class FilesSearchQuery(
    public val keyword: String,
    public val perPage: Int? = null,
    public val type: List<PutioFileType> = emptyList(),
)

internal fun FilesSearchQuery.toQueryMap(): Map<String, String> =
    buildMap {
        put("query", keyword)
        if (perPage != null) put("per_page", perPage.toString())
        if (type.isNotEmpty()) put("type", type.joinToString(",") { it.raw })
    }

public data class FilesContinueQuery(
    public val perPage: Int? = null,
)

internal fun FilesContinueQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
    }

public data class FilesListQuery(
    public val perPage: Int? = null,
    public val total: Boolean = false,
    public val hidden: Boolean = false,
    public val noCursor: Boolean = false,
    public val contentType: String? = null,
    public val fileType: PutioFileType? = null,
    public val sortBy: String? = null,
    public val mp4Status: Boolean = false,
    /** Include `video_metadata` (duration, codec, dimensions) on each listed child. */
    public val videoMetadata: Boolean = false,
    /** Ask the API to sign `stream_url` on the listed parent file. */
    public val streamUrlParent: Boolean = false,
    /** Ask the API to sign `mp4_stream_url` on the listed parent file. */
    public val mp4StreamUrlParent: Boolean = false,
)

internal fun FilesListQuery.toQueryMap(parentId: Long): Map<String, String> =
    buildMap {
        put("parent_id", parentId.toString())
        put("mp4_status_parent", "1")
        if (streamUrlParent) put("stream_url_parent", "1")
        if (mp4StreamUrlParent) put("mp4_stream_url_parent", "1")
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

public data class FileDetailsQuery(
    public val mp4Size: Boolean = false,
    public val startFrom: Boolean = true,
    public val streamUrl: Boolean = false,
    public val mp4StreamUrl: Boolean = false,
    public val mp4Status: Boolean = false,
    public val mediaInfo: Boolean = false,
)

internal fun FileDetailsQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (mediaInfo) put("media_info", "1")
        if (mp4Size) put("mp4_size", "1")
        if (mp4Status) put("mp4_status", "1")
        if (startFrom) put("start_from", "1")
        if (streamUrl) put("stream_url", "1")
        if (mp4StreamUrl) put("mp4_stream_url", "1")
    }

@Serializable
public data class FileSubtitle(
    public val format: String? = null,
    public val key: String,
    public val language: String,
    @SerialName("language_code") public val languageCode: String,
    public val name: String,
    public val source: String,
    public val url: String,
)

/**
 * [defaultKey] decodes `default`: the [FileSubtitle.key] put.io names as the list's default, or null
 * when the list is empty.
 */
@Serializable
public data class FileSubtitlesResponse(
    @SerialName("default") public val defaultKey: String? = null,
    public val subtitles: List<FileSubtitle> = emptyList(),
    public val status: String,
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
public data class FileDeleteResult(
    public val cursor: String? = null,
    public val skipped: Int = 0,
    public val status: String,
) {
    init {
        require(status == "OK") { "Delete result status must be OK" }
        require(skipped >= 0) { "Delete result skipped count must be nonnegative" }
        require(cursor == null || cursor.isNotBlank()) { "Delete result cursor must not be blank" }
    }
}

@Serializable
public data class FileMoveError(
    @SerialName("error_type") public val errorType: String,
    public val id: Long,
    public val name: String? = null,
    @SerialName("status_code") public val statusCode: Int,
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
public value class NextFileType(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = this in knownValues

    public override fun toString(): String = raw

    public companion object {
        public val VIDEO: NextFileType = NextFileType("VIDEO")
        public val AUDIO: NextFileType = NextFileType("AUDIO")

        private val knownValues = setOf(VIDEO, AUDIO)

        public fun fromRaw(raw: String): NextFileType =
            when (raw) {
                VIDEO.raw -> VIDEO
                AUDIO.raw -> AUDIO
                else -> NextFileType(raw)
            }
    }

    public object Serializer : RawStringValueSerializer<NextFileType>("NextFileType") {
        override fun fromRaw(raw: String): NextFileType = Companion.fromRaw(raw)

        override fun toRaw(value: NextFileType): String = value.raw
    }
}

@Serializable
public data class NextFile(
    public val id: Long,
    public val name: String,
    @SerialName("parent_id") public val parentId: Long? = null,
    @SerialName("file_type") public val fileType: NextFileType? = null,
)

@Serializable
internal data class NextFileEnvelope(
    @SerialName("next_file") val nextFile: NextFile,
    val status: String,
)

@Serializable(with = FileMp4ConversionStatus.Serializer::class)
@JvmInline
public value class FileMp4ConversionStatus(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = this in knownValues

    public override fun toString(): String = raw

    public companion object {
        public val IN_QUEUE: FileMp4ConversionStatus = FileMp4ConversionStatus("IN_QUEUE")
        public val CONVERTING: FileMp4ConversionStatus = FileMp4ConversionStatus("CONVERTING")
        public val COMPLETED: FileMp4ConversionStatus = FileMp4ConversionStatus("COMPLETED")
        public val ERROR: FileMp4ConversionStatus = FileMp4ConversionStatus("ERROR")
        public val NOT_AVAILABLE: FileMp4ConversionStatus = FileMp4ConversionStatus("NOT_AVAILABLE")

        private val knownValues = setOf(IN_QUEUE, CONVERTING, COMPLETED, ERROR, NOT_AVAILABLE)

        public fun fromRaw(raw: String): FileMp4ConversionStatus =
            when (raw) {
                IN_QUEUE.raw -> IN_QUEUE
                CONVERTING.raw -> CONVERTING
                COMPLETED.raw -> COMPLETED
                ERROR.raw -> ERROR
                NOT_AVAILABLE.raw -> NOT_AVAILABLE
                else -> FileMp4ConversionStatus(raw)
            }
    }

    public object Serializer : RawStringValueSerializer<FileMp4ConversionStatus>("FileMp4ConversionStatus") {
        override fun fromRaw(raw: String): FileMp4ConversionStatus = Companion.fromRaw(raw)

        override fun toRaw(value: FileMp4ConversionStatus): String = value.raw
    }
}

@Serializable
public data class FileMp4Conversion(
    public val id: Long? = null,
    @SerialName("percent_done") public val percentDone: Double? = null,
    public val size: Long? = null,
    public val status: FileMp4ConversionStatus,
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
public class FileUploadInput(
    public val content: ByteArray,
    public val fileName: String,
    public val parentId: Long? = null,
    public val requireTorrent: Boolean = false,
    public val mediaType: String = "application/octet-stream",
) {
    init {
        require(fileName.isNotBlank()) { "Upload file name must not be blank" }
        require(parentId == null || parentId >= 0) { "Upload parent id must not be negative" }
        require(!requireTorrent || content.isNotEmpty()) { "Torrent upload content must not be empty" }
        // OkHttp accepts CR/LF in quoted parameters and MultipartBody writes them into the part header.
        require(mediaType.none(Char::isISOControl) && mediaType.toMediaTypeOrNull() != null) {
            "Upload media type must be a valid media type"
        }
    }

    internal fun toFormMap(): Map<String, String> =
        buildMap {
            put("filename", fileName)
            parentId?.let { put("parent_id", it.toString()) }
            if (requireTorrent) put("torrent", "true")
        }

    public override fun toString(): String =
        "FileUploadInput(fileName=$fileName, size=${content.size}, parentId=$parentId, requireTorrent=$requireTorrent)"
}

public sealed interface FileUploadResult {
    public data class File(
        public val file: PutioFile,
    ) : FileUploadResult

    public data class Transfer(
        public val transfer: io.putdotio.sdk.transfers.Transfer,
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
