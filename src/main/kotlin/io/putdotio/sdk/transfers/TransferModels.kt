package io.putdotio.sdk.transfers

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = TransferType.Serializer::class)
@JvmInline
value class TransferType(val raw: String) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val URL = TransferType("URL")
        val TORRENT = TransferType("TORRENT")
        val PLAYLIST = TransferType("PLAYLIST")
        val LIVE_STREAM = TransferType("LIVE_STREAM")
        val NOT_AVAILABLE = TransferType("N/A")

        private val knownValues = setOf(URL, TORRENT, PLAYLIST, LIVE_STREAM, NOT_AVAILABLE)

        fun fromRaw(raw: String): TransferType =
            when (raw) {
                URL.raw -> URL
                TORRENT.raw -> TORRENT
                PLAYLIST.raw -> PLAYLIST
                LIVE_STREAM.raw -> LIVE_STREAM
                NOT_AVAILABLE.raw -> NOT_AVAILABLE
                else -> TransferType(raw)
            }
    }

    object Serializer : RawStringValueSerializer<TransferType>("TransferType") {
        override fun fromRaw(raw: String): TransferType = Companion.fromRaw(raw)

        override fun toRaw(value: TransferType): String = value.raw
    }
}

@Serializable(with = TransferStatus.Serializer::class)
@JvmInline
value class TransferStatus(val raw: String) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val WAITING = TransferStatus("WAITING")
        val PREPARING_DOWNLOAD = TransferStatus("PREPARING_DOWNLOAD")
        val IN_QUEUE = TransferStatus("IN_QUEUE")
        val DOWNLOADING = TransferStatus("DOWNLOADING")
        val WAITING_FOR_COMPLETE_QUEUE = TransferStatus("WAITING_FOR_COMPLETE_QUEUE")
        val WAITING_FOR_DOWNLOADER = TransferStatus("WAITING_FOR_DOWNLOADER")
        val COMPLETING = TransferStatus("COMPLETING")
        val STOPPING = TransferStatus("STOPPING")
        val SEEDING = TransferStatus("SEEDING")
        val COMPLETED = TransferStatus("COMPLETED")
        val ERROR = TransferStatus("ERROR")
        val PREPARING_SEED = TransferStatus("PREPARING_SEED")

        private val knownValues = setOf(
            WAITING,
            PREPARING_DOWNLOAD,
            IN_QUEUE,
            DOWNLOADING,
            WAITING_FOR_COMPLETE_QUEUE,
            WAITING_FOR_DOWNLOADER,
            COMPLETING,
            STOPPING,
            SEEDING,
            COMPLETED,
            ERROR,
            PREPARING_SEED,
        )

        fun fromRaw(raw: String): TransferStatus =
            when (raw) {
                WAITING.raw -> WAITING
                PREPARING_DOWNLOAD.raw -> PREPARING_DOWNLOAD
                IN_QUEUE.raw -> IN_QUEUE
                DOWNLOADING.raw -> DOWNLOADING
                WAITING_FOR_COMPLETE_QUEUE.raw -> WAITING_FOR_COMPLETE_QUEUE
                WAITING_FOR_DOWNLOADER.raw -> WAITING_FOR_DOWNLOADER
                COMPLETING.raw -> COMPLETING
                STOPPING.raw -> STOPPING
                SEEDING.raw -> SEEDING
                COMPLETED.raw -> COMPLETED
                ERROR.raw -> ERROR
                PREPARING_SEED.raw -> PREPARING_SEED
                else -> TransferStatus(raw)
            }
    }

    object Serializer : RawStringValueSerializer<TransferStatus>("TransferStatus") {
        override fun fromRaw(raw: String): TransferStatus = Companion.fromRaw(raw)

        override fun toRaw(value: TransferStatus): String = value.raw
    }
}

@Serializable
data class TransferLink(
    val label: String,
    val url: String? = null,
)

@Serializable
data class Transfer(
    val id: Long,
    val name: String,
    val source: String = "",
    val type: TransferType = TransferType.NOT_AVAILABLE,
    val status: TransferStatus = TransferStatus.WAITING,
    @SerialName("save_parent_id") val saveParentId: Long = 0,
    @SerialName("file_id") val fileId: Long? = null,
    @SerialName("download_id") val downloadId: Long? = null,
    val size: Double? = null,
    @SerialName("percent_done") val percentDone: Double? = null,
    @SerialName("completion_percent") val completionPercent: Double? = null,
    val downloaded: Double? = null,
    val uploaded: Double? = null,
    @SerialName("down_speed") val downSpeed: Double? = null,
    @SerialName("up_speed") val upSpeed: Double? = null,
    @SerialName("estimated_time") val estimatedTime: Double? = null,
    val availability: Double? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("callback_url") val callbackUrl: String? = null,
    @SerialName("current_ratio") val currentRatio: Double? = null,
    @SerialName("seconds_seeding") val secondsSeeding: Double? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    val links: List<TransferLink> = emptyList(),
    @SerialName("userfile_exists") val userFileExists: Boolean? = null,
)

@Serializable
internal data class TransferEnvelope(
    val transfer: Transfer,
    val status: String,
)

@Serializable
internal data class TransferCountEnvelope(
    val count: Int,
    val status: String,
)

@Serializable
data class TransfersListResponse(
    val cursor: String? = null,
    val total: Int? = null,
    val transfers: List<Transfer> = emptyList(),
    val status: String,
)

data class TransfersListQuery(
    val perPage: Int? = null,
)

internal fun TransfersListQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
    }

@Serializable
data class TransferAddInput(
    val url: String,
    @SerialName("save_parent_id") val saveParentId: Long? = null,
    @SerialName("callback_url") val callbackUrl: String? = null,
) {
    internal fun toFormMap(): Map<String, String> =
        buildMap {
            put("url", url)
            if (saveParentId != null) put("save_parent_id", saveParentId.toString())
            if (callbackUrl != null) put("callback_url", callbackUrl)
        }
}

@Serializable
data class TransferInfoItem(
    val url: String,
    val name: String,
    @SerialName("type_name") val typeName: String,
    @SerialName("file_size") val fileSize: Double,
    @SerialName("human_size") val humanSize: String,
    val error: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
data class TransferInfoResponse(
    @SerialName("disk_avail") val diskAvailable: Double,
    @SerialName("ret") val items: List<TransferInfoItem> = emptyList(),
    val status: String,
)

@Serializable
data class TransfersAddManyError(
    @SerialName("error_type") val errorType: String,
    @SerialName("status_code") val statusCode: Int,
    val url: String,
)

@Serializable
data class TransfersAddManyResponse(
    val errors: List<TransfersAddManyError> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
    val status: String,
)

@Serializable
data class TransfersCleanResponse(
    @SerialName("deleted_ids") val deletedIds: List<Long> = emptyList(),
    val status: String,
)
