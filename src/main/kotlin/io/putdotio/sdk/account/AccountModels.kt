package io.putdotio.sdk.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AccountInfoEnvelope(
    val info: AccountInfo,
    val status: String,
)

@Serializable
internal data class AccountSettingsEnvelope(
    val settings: AccountSettings,
    val status: String,
)

@Serializable
data class AccountDisk(
    @SerialName("avail") val available: Long,
    val size: Long,
    val used: Long,
)

@Serializable
data class AccountSettings(
    @SerialName("sort_by") val sortBy: String,
    @SerialName("tunnel_route_name") val tunnelRouteName: String? = null,
    @SerialName("next_episode") val nextEpisode: Boolean = false,
    @SerialName("start_from") val startFrom: Boolean = false,
    @SerialName("history_enabled") val historyEnabled: Boolean = false,
    @SerialName("trash_enabled") val trashEnabled: Boolean = false,
    @SerialName("show_optimistic_usage") val showOptimisticUsage: Boolean = false,
    @SerialName("two_factor_enabled") val twoFactorEnabled: Boolean = false,
    @SerialName("hide_subtitles") val hideSubtitles: Boolean = false,
    @SerialName("dont_autoselect_subtitles") val dontAutoselectSubtitles: Boolean = false,
)

@Serializable
data class AccountInfo(
    @SerialName("user_id") val userId: Long,
    val username: String,
    val mail: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val disk: AccountDisk,
    val settings: AccountSettings,
    @SerialName("account_status") val accountStatus: String,
    @SerialName("trash_size") val trashSize: Long = 0,
    @SerialName("account_active") val accountActive: Boolean? = null,
    @SerialName("download_token") val downloadToken: String? = null,
    val features: Map<String, Boolean> = emptyMap(),
    @SerialName("files_will_be_deleted_at") val filesWillBeDeletedAt: String? = null,
    @SerialName("password_last_changed_at") val passwordLastChangedAt: String? = null,
    @SerialName("user_hash") val userHash: String? = null,
)

data class AccountInfoQuery(
    val downloadToken: Boolean = false,
    val features: Boolean = false,
    val intercom: Boolean = false,
    val pas: Boolean = false,
    val platform: String? = null,
    val profitwell: Boolean = false,
    val pushToken: Boolean = false,
)

internal fun AccountInfoQuery.toQueryMap(): Map<String, String> =
    buildMap {
        if (downloadToken) put("download_token", "1")
        if (features) put("features", "1")
        if (intercom) put("intercom", "1")
        if (pas) put("pas", "1")
        if (platform != null) put("platform", platform)
        if (profitwell) put("profitwell", "1")
        if (pushToken) put("push_token", "1")
    }

@Serializable
data class AccountTwoFactorSettings(
    val code: String,
    val enable: Boolean,
)

@Serializable
data class AccountSettingsUpdate(
    @SerialName("history_enabled") val historyEnabled: Boolean? = null,
    @SerialName("trash_enabled") val trashEnabled: Boolean? = null,
    @SerialName("hide_subtitles") val hideSubtitles: Boolean? = null,
    @SerialName("dont_autoselect_subtitles") val dontAutoselectSubtitles: Boolean? = null,
    @SerialName("tunnel_route_name") val tunnelRouteName: String? = null,
    @SerialName("show_optimistic_usage") val showOptimisticUsage: Boolean? = null,
    @SerialName("two_factor_enabled") val twoFactorEnabled: AccountTwoFactorSettings? = null,
)
