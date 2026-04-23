package io.putdotio.sdk.history

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = HistoryEventType.Serializer::class)
@JvmInline
value class HistoryEventType(val raw: String) {
    val isKnown: Boolean
        get() = this in knownValues

    override fun toString(): String = raw

    companion object {
        val UPLOAD = HistoryEventType("UPLOAD")
        val FILE_SHARED = HistoryEventType("FILE_SHARED")
        val TRANSFER_COMPLETED = HistoryEventType("TRANSFER_COMPLETED")
        val TRANSFER_ERROR = HistoryEventType("TRANSFER_ERROR")
        val FILE_FROM_RSS_DELETED_ERROR = HistoryEventType("FILE_FROM_RSS_DELETED_ERROR")
        val RSS_FILTER_PAUSED = HistoryEventType("RSS_FILTER_PAUSED")
        val TRANSFER_FROM_RSS_ERROR = HistoryEventType("TRANSFER_FROM_RSS_ERROR")
        val TRANSFER_CALLBACK_ERROR = HistoryEventType("TRANSFER_CALLBACK_ERROR")
        val PRIVATE_TORRENT_PIN = HistoryEventType("PRIVATE_TORRENT_PIN")
        val VOUCHER = HistoryEventType("VOUCHER")
        val ZIP_CREATED = HistoryEventType("ZIP_CREATED")
        val OTHER = HistoryEventType("OTHER")

        private val knownValues = setOf(
            UPLOAD,
            FILE_SHARED,
            TRANSFER_COMPLETED,
            TRANSFER_ERROR,
            FILE_FROM_RSS_DELETED_ERROR,
            RSS_FILTER_PAUSED,
            TRANSFER_FROM_RSS_ERROR,
            TRANSFER_CALLBACK_ERROR,
            PRIVATE_TORRENT_PIN,
            VOUCHER,
            ZIP_CREATED,
            OTHER,
        )

        fun fromRaw(raw: String): HistoryEventType =
            when (raw) {
                UPLOAD.raw -> UPLOAD
                FILE_SHARED.raw -> FILE_SHARED
                TRANSFER_COMPLETED.raw -> TRANSFER_COMPLETED
                TRANSFER_ERROR.raw -> TRANSFER_ERROR
                FILE_FROM_RSS_DELETED_ERROR.raw -> FILE_FROM_RSS_DELETED_ERROR
                RSS_FILTER_PAUSED.raw -> RSS_FILTER_PAUSED
                TRANSFER_FROM_RSS_ERROR.raw -> TRANSFER_FROM_RSS_ERROR
                TRANSFER_CALLBACK_ERROR.raw -> TRANSFER_CALLBACK_ERROR
                PRIVATE_TORRENT_PIN.raw -> PRIVATE_TORRENT_PIN
                VOUCHER.raw -> VOUCHER
                ZIP_CREATED.raw -> ZIP_CREATED
                OTHER.raw -> OTHER
                else -> HistoryEventType(raw)
            }
    }

    object Serializer : RawStringValueSerializer<HistoryEventType>("HistoryEventType") {
        override fun fromRaw(raw: String): HistoryEventType = Companion.fromRaw(raw)

        override fun toRaw(value: HistoryEventType): String = value.raw
    }
}

@Serializable
data class HistoryEvent(
    val id: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("created_at") val createdAt: String,
    val type: HistoryEventType = HistoryEventType.OTHER,
    @SerialName("file_id") val fileId: Long? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("transfer_id") val transferId: Long? = null,
    @SerialName("transfer_name") val transferName: String? = null,
    @SerialName("rss_filter_title") val rssFilterTitle: String? = null,
    @SerialName("zip_id") val zipId: Long? = null,
    val icon: String? = null,
)

@Serializable
data class HistoryListResponse(
    val events: List<HistoryEvent>,
    @SerialName("has_more") val hasMore: Boolean,
    val status: String,
)

data class HistoryListQuery(
    val perPage: Int? = null,
    val before: Long? = null,
)

internal fun HistoryListQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
        if (before != null) put("before", before.toString())
    }
