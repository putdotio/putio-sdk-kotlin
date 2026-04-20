package io.putdotio.sdk.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HistoryEventType {
    @SerialName("UPLOAD") UPLOAD,
    @SerialName("FILE_SHARED") FILE_SHARED,
    @SerialName("TRANSFER_COMPLETED") TRANSFER_COMPLETED,
    @SerialName("TRANSFER_ERROR") TRANSFER_ERROR,
    @SerialName("FILE_FROM_RSS_DELETED_ERROR") FILE_FROM_RSS_DELETED_ERROR,
    @SerialName("RSS_FILTER_PAUSED") RSS_FILTER_PAUSED,
    @SerialName("TRANSFER_FROM_RSS_ERROR") TRANSFER_FROM_RSS_ERROR,
    @SerialName("TRANSFER_CALLBACK_ERROR") TRANSFER_CALLBACK_ERROR,
    @SerialName("PRIVATE_TORRENT_PIN") PRIVATE_TORRENT_PIN,
    @SerialName("VOUCHER") VOUCHER,
    @SerialName("ZIP_CREATED") ZIP_CREATED,
    @SerialName("OTHER") OTHER,
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
internal data class HistoryEventsEnvelope(
    val events: List<HistoryEvent>,
    val status: String,
)

data class HistoryListQuery(
    val perPage: Int? = null,
    val page: Int? = null,
)

internal fun HistoryListQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (perPage != null) put("per_page", perPage.toString())
        if (page != null) put("page", page.toString())
    }

