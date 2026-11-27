package io.putdotio.sdk.transfers

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = TransferType.Serializer::class)
@JvmInline
public value class TransferType(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = this in knownValues

    public override fun toString(): String = raw

    public companion object {
        public val URL: TransferType = TransferType("URL")
        public val TORRENT: TransferType = TransferType("TORRENT")
        public val PLAYLIST: TransferType = TransferType("PLAYLIST")
        public val LIVE_STREAM: TransferType = TransferType("LIVE_STREAM")
        public val NOT_AVAILABLE: TransferType = TransferType("N/A")

        private val knownValues = setOf(URL, TORRENT, PLAYLIST, LIVE_STREAM, NOT_AVAILABLE)

        public fun fromRaw(raw: String): TransferType =
            when (raw) {
                URL.raw -> URL
                TORRENT.raw -> TORRENT
                PLAYLIST.raw -> PLAYLIST
                LIVE_STREAM.raw -> LIVE_STREAM
                NOT_AVAILABLE.raw -> NOT_AVAILABLE
                else -> TransferType(raw)
            }
    }

    public object Serializer : RawStringValueSerializer<TransferType>("TransferType") {
        override fun fromRaw(raw: String): TransferType = Companion.fromRaw(raw)

        override fun toRaw(value: TransferType): String = value.raw
    }
}

@Serializable(with = TransferStatus.Serializer::class)
@JvmInline
public value class TransferStatus(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = this in knownValues

    public override fun toString(): String = raw

    public companion object {
        public val WAITING: TransferStatus = TransferStatus("WAITING")
        public val PREPARING_DOWNLOAD: TransferStatus = TransferStatus("PREPARING_DOWNLOAD")
        public val IN_QUEUE: TransferStatus = TransferStatus("IN_QUEUE")
        public val DOWNLOADING: TransferStatus = TransferStatus("DOWNLOADING")
        public val WAITING_FOR_COMPLETE_QUEUE: TransferStatus = TransferStatus("WAITING_FOR_COMPLETE_QUEUE")
        public val WAITING_FOR_DOWNLOADER: TransferStatus = TransferStatus("WAITING_FOR_DOWNLOADER")
        public val COMPLETING: TransferStatus = TransferStatus("COMPLETING")
        public val STOPPING: TransferStatus = TransferStatus("STOPPING")
        public val SEEDING: TransferStatus = TransferStatus("SEEDING")
        public val COMPLETED: TransferStatus = TransferStatus("COMPLETED")
        public val ERROR: TransferStatus = TransferStatus("ERROR")
        public val PREPARING_SEED: TransferStatus = TransferStatus("PREPARING_SEED")

        private val knownValues =
            setOf(
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

        public fun fromRaw(raw: String): TransferStatus =
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

    public object Serializer : RawStringValueSerializer<TransferStatus>("TransferStatus") {
        override fun fromRaw(raw: String): TransferStatus = Companion.fromRaw(raw)

        override fun toRaw(value: TransferStatus): String = value.raw
    }
}

@Serializable
public data class TransferLink(
    public val label: String,
    public val url: String? = null,
)

@Serializable
public data class Transfer(
    public val id: Long,
    public val name: String,
    public val source: String = "",
    public val type: TransferType = TransferType.NOT_AVAILABLE,
    public val status: TransferStatus,
    @SerialName("save_parent_id") public val saveParentId: Long,
    @SerialName("file_id") public val fileId: Long? = null,
    @SerialName("download_id") public val downloadId: Long? = null,
    public val size: Double? = null,
    @SerialName("percent_done") public val percentDone: Double? = null,
    @SerialName("completion_percent") public val completionPercent: Double? = null,
    public val downloaded: Double? = null,
    public val uploaded: Double? = null,
    @SerialName("down_speed") public val downSpeed: Double? = null,
    @SerialName("up_speed") public val upSpeed: Double? = null,
    @SerialName("estimated_time") public val estimatedTime: Double? = null,
    public val availability: Double? = null,
    @SerialName("error_message") public val errorMessage: String? = null,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("started_at") public val startedAt: String? = null,
    @SerialName("finished_at") public val finishedAt: String? = null,
    @SerialName("callback_url") public val callbackUrl: String? = null,
    @SerialName("current_ratio") public val currentRatio: Double? = null,
    @SerialName("seconds_seeding") public val secondsSeeding: Double? = null,
    @SerialName("is_private") public val isPrivate: Boolean = false,
    public val links: List<TransferLink> = emptyList(),
    @SerialName("userfile_exists") public val userFileExists: Boolean? = null,
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
public data class TransfersListResponse(
    public val cursor: String? = null,
    public val total: Int? = null,
    public val transfers: List<Transfer> = emptyList(),
    public val status: String,
)

public data class TransfersListQuery(
    public val perPage: Int? = null,
)

internal fun TransfersListQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
    }

@Serializable
public data class TransferAddInput(
    public val url: String,
    @SerialName("save_parent_id") public val saveParentId: Long? = null,
    @SerialName("callback_url") public val callbackUrl: String? = null,
) {
    internal fun toFormMap(): Map<String, String> =
        buildMap {
            put("url", url)
            if (saveParentId != null) put("save_parent_id", saveParentId.toString())
            if (callbackUrl != null) put("callback_url", callbackUrl)
        }
}

@Serializable
public data class TransferInfoItem(
    public val url: String,
    public val name: String,
    @SerialName("type_name") public val typeName: String,
    @SerialName("file_size") public val fileSize: Double,
    @SerialName("human_size") public val humanSize: String,
    public val error: String? = null,
    @SerialName("error_message") public val errorMessage: String? = null,
)

@Serializable
public data class TransferInfoResponse(
    @SerialName("disk_avail") public val diskAvailable: Double,
    @SerialName("ret") public val items: List<TransferInfoItem> = emptyList(),
    public val status: String,
)

@Serializable
public data class TransfersAddManyError(
    @SerialName("error_type") public val errorType: String,
    @SerialName("status_code") public val statusCode: Int,
    public val url: String,
)

@Serializable
public data class TransfersAddManyResponse(
    public val errors: List<TransfersAddManyError> = emptyList(),
    public val transfers: List<Transfer> = emptyList(),
    public val status: String,
)

@Serializable
public data class TransfersCleanResponse(
    @SerialName("deleted_ids") public val deletedIds: List<Long> = emptyList(),
    public val status: String,
)
