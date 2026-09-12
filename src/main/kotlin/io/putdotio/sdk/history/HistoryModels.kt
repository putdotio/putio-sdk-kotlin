package io.putdotio.sdk.history

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = HistoryEventType.Serializer::class)
@JvmInline
value class HistoryEventType(
    val raw: String,
) {
    val isKnown: Boolean
        get() = raw in knownValues

    override fun toString(): String = raw

    companion object {
        // Wire values as the API sends them, lowercase; see putio-sdk-typescript events.ts.
        val UPLOAD = HistoryEventType("upload")
        val FILE_SHARED = HistoryEventType("file_shared")
        val TRANSFER_COMPLETED = HistoryEventType("transfer_completed")
        val TRANSFER_ERROR = HistoryEventType("transfer_error")
        val FILE_FROM_RSS_DELETED_FOR_SPACE = HistoryEventType("file_from_rss_deleted_for_space")
        val RSS_FILTER_PAUSED = HistoryEventType("rss_filter_paused")
        val TRANSFER_FROM_RSS_ERROR = HistoryEventType("transfer_from_rss_error")
        val TRANSFER_CALLBACK_ERROR = HistoryEventType("transfer_callback_error")
        val PRIVATE_TORRENT_PIN = HistoryEventType("private_torrent_pin")
        val VOUCHER = HistoryEventType("voucher")
        val ZIP_CREATED = HistoryEventType("zip_created")
        val OTHER = HistoryEventType("other")

        @Deprecated(
            "The API sends file_from_rss_deleted_for_space; this name never matched an event.",
            ReplaceWith("FILE_FROM_RSS_DELETED_FOR_SPACE"),
        )
        val FILE_FROM_RSS_DELETED_ERROR: HistoryEventType
            get() = FILE_FROM_RSS_DELETED_FOR_SPACE

        private val knownValues =
            listOf(
                UPLOAD,
                FILE_SHARED,
                TRANSFER_COMPLETED,
                TRANSFER_ERROR,
                FILE_FROM_RSS_DELETED_FOR_SPACE,
                RSS_FILTER_PAUSED,
                TRANSFER_FROM_RSS_ERROR,
                TRANSFER_CALLBACK_ERROR,
                PRIVATE_TORRENT_PIN,
                VOUCHER,
                ZIP_CREATED,
                OTHER,
            ).associateBy { it.raw }

        /** Known types match regardless of case and keep their canonical raw value; others keep theirs. */
        fun fromRaw(raw: String): HistoryEventType = knownValues[raw.lowercase()] ?: HistoryEventType(raw)
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
