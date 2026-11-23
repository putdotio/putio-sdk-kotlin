package io.putdotio.sdk.history

import io.putdotio.sdk.core.RawStringValueSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = HistoryEventType.Serializer::class)
@JvmInline
public value class HistoryEventType(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = raw in knownValues

    public override fun toString(): String = raw

    public companion object {
        // Wire values as the API sends them, lowercase; see putio-sdk-typescript events.ts.
        public val UPLOAD: HistoryEventType = HistoryEventType("upload")
        public val FILE_SHARED: HistoryEventType = HistoryEventType("file_shared")
        public val TRANSFER_COMPLETED: HistoryEventType = HistoryEventType("transfer_completed")
        public val TRANSFER_ERROR: HistoryEventType = HistoryEventType("transfer_error")
        public val FILE_FROM_RSS_DELETED_FOR_SPACE: HistoryEventType = HistoryEventType("file_from_rss_deleted_for_space")
        public val RSS_FILTER_PAUSED: HistoryEventType = HistoryEventType("rss_filter_paused")
        public val TRANSFER_FROM_RSS_ERROR: HistoryEventType = HistoryEventType("transfer_from_rss_error")
        public val TRANSFER_CALLBACK_ERROR: HistoryEventType = HistoryEventType("transfer_callback_error")
        public val PRIVATE_TORRENT_PIN: HistoryEventType = HistoryEventType("private_torrent_pin")
        public val VOUCHER: HistoryEventType = HistoryEventType("voucher")
        public val ZIP_CREATED: HistoryEventType = HistoryEventType("zip_created")
        public val OTHER: HistoryEventType = HistoryEventType("other")

        @Deprecated(
            "The API sends file_from_rss_deleted_for_space; this name never matched an event.",
            ReplaceWith("FILE_FROM_RSS_DELETED_FOR_SPACE"),
        )
        public val FILE_FROM_RSS_DELETED_ERROR: HistoryEventType
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
        public fun fromRaw(raw: String): HistoryEventType = knownValues[raw.lowercase()] ?: HistoryEventType(raw)
    }

    public object Serializer : RawStringValueSerializer<HistoryEventType>("HistoryEventType") {
        override fun fromRaw(raw: String): HistoryEventType = Companion.fromRaw(raw)

        override fun toRaw(value: HistoryEventType): String = value.raw
    }
}

@Serializable
public data class HistoryEvent(
    public val id: Long,
    @SerialName("user_id") public val userId: Long,
    @SerialName("created_at") public val createdAt: String,
    public val type: HistoryEventType = HistoryEventType.OTHER,
    @SerialName("file_id") public val fileId: Long? = null,
    @SerialName("file_name") public val fileName: String? = null,
    @SerialName("transfer_id") public val transferId: Long? = null,
    @SerialName("transfer_name") public val transferName: String? = null,
    @SerialName("rss_filter_title") public val rssFilterTitle: String? = null,
    @SerialName("zip_id") public val zipId: Long? = null,
    public val icon: String? = null,
    /** Size in bytes of the file the event names, sent on `upload`, `file_shared` and `file_from_rss_deleted_for_space`. */
    @SerialName("file_size") public val fileSize: Long? = null,
    /** Name of the user who shared the file, sent on `file_shared`. */
    @SerialName("sharing_user_name") public val sharingUserName: String? = null,
    /** Total size in bytes of the completed transfer, sent on `transfer_completed`. */
    @SerialName("transfer_size") public val transferSize: Long? = null,
)

@Serializable
public data class HistoryListResponse(
    public val events: List<HistoryEvent>,
    @SerialName("has_more") public val hasMore: Boolean,
    public val status: String,
)

public data class HistoryListQuery(
    public val perPage: Int? = null,
    public val before: Long? = null,
)

internal fun HistoryListQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
        if (before != null) put("before", before.toString())
    }
