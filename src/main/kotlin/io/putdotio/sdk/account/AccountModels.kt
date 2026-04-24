package io.putdotio.sdk.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

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

@Serializable(with = AccountSettingsUpdateSerializer::class)
sealed interface AccountSettingsUpdate

@Serializable
data class AccountSettingsPatch(
    @SerialName("history_enabled") val historyEnabled: Boolean? = null,
    @SerialName("trash_enabled") val trashEnabled: Boolean? = null,
    @SerialName("hide_subtitles") val hideSubtitles: Boolean? = null,
    @SerialName("dont_autoselect_subtitles") val dontAutoselectSubtitles: Boolean? = null,
    @SerialName("tunnel_route_name") val tunnelRouteName: String? = null,
    @SerialName("show_optimistic_usage") val showOptimisticUsage: Boolean? = null,
    @SerialName("sort_by") val sortBy: String? = null,
): AccountSettingsUpdate

@Serializable
data class AccountUsernameUpdate(
    val username: String,
): AccountSettingsUpdate

@Serializable
data class AccountMailUpdate(
    @SerialName("current_password") val currentPassword: String,
    val mail: String,
): AccountSettingsUpdate

@Serializable
data class AccountPasswordUpdate(
    @SerialName("current_password") val currentPassword: String,
    val password: String,
): AccountSettingsUpdate

@Serializable
data class AccountTwoFactorUpdate(
    @SerialName("two_factor_enabled") val twoFactorEnabled: AccountTwoFactorSettings,
): AccountSettingsUpdate

@Serializable
data class AccountClearOptions(
    val files: Boolean = false,
    @SerialName("finished_transfers") val finishedTransfers: Boolean = false,
    @SerialName("active_transfers") val activeTransfers: Boolean = false,
    @SerialName("rss_feeds") val rssFeeds: Boolean = false,
    @SerialName("rss_logs") val rssLogs: Boolean = false,
    val history: Boolean = false,
    val trash: Boolean = false,
    val friends: Boolean = false,
)

@Serializable
internal data class AccountDestroyInput(
    @SerialName("current_password") val currentPassword: String,
)

object AccountSettingsUpdateSerializer : KSerializer<AccountSettingsUpdate> {
    override val descriptor = buildClassSerialDescriptor("AccountSettingsUpdate")

    override fun serialize(
        encoder: Encoder,
        value: AccountSettingsUpdate,
    ) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("AccountSettingsUpdate requires JSON encoding")

        val element = when (value) {
            is AccountSettingsPatch -> jsonEncoder.json.encodeToJsonElement(AccountSettingsPatch.serializer(), value)
            is AccountUsernameUpdate -> jsonEncoder.json.encodeToJsonElement(AccountUsernameUpdate.serializer(), value)
            is AccountMailUpdate -> jsonEncoder.json.encodeToJsonElement(AccountMailUpdate.serializer(), value)
            is AccountPasswordUpdate -> jsonEncoder.json.encodeToJsonElement(AccountPasswordUpdate.serializer(), value)
            is AccountTwoFactorUpdate -> jsonEncoder.json.encodeToJsonElement(AccountTwoFactorUpdate.serializer(), value)
        }

        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): AccountSettingsUpdate {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("AccountSettingsUpdate requires JSON decoding")
        val element = jsonDecoder.decodeJsonElement()
        val jsonObject = element as? kotlinx.serialization.json.JsonObject
            ?: throw SerializationException("Expected JSON object for AccountSettingsUpdate")

        return when {
            "two_factor_enabled" in jsonObject ->
                jsonDecoder.json.decodeFromJsonElement(AccountTwoFactorUpdate.serializer(), element)
            "username" in jsonObject ->
                jsonDecoder.json.decodeFromJsonElement(AccountUsernameUpdate.serializer(), element)
            "mail" in jsonObject || "current_password" in jsonObject && "password" !in jsonObject ->
                jsonDecoder.json.decodeFromJsonElement(AccountMailUpdate.serializer(), element)
            "password" in jsonObject ->
                jsonDecoder.json.decodeFromJsonElement(AccountPasswordUpdate.serializer(), element)
            else ->
                jsonDecoder.json.decodeFromJsonElement(AccountSettingsPatch.serializer(), element)
        }
    }
}
