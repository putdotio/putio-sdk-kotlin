package io.putdotio.sdk.account

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
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
public data class AccountDisk(
    @SerialName("avail") public val available: Long,
    public val size: Long,
    public val used: Long,
)

@Serializable
public data class AccountSettings(
    @SerialName("sort_by") public val sortBy: String,
    @SerialName("tunnel_route_name") public val tunnelRouteName: String? = null,
    @SerialName("next_episode") public val nextEpisode: Boolean = false,
    @SerialName("use_start_from") public val useStartFrom: Boolean = false,
    @SerialName("history_enabled") public val historyEnabled: Boolean = false,
    @SerialName("trash_enabled") public val trashEnabled: Boolean = false,
    @SerialName("show_optimistic_usage") public val showOptimisticUsage: Boolean = false,
    @SerialName("two_factor_enabled") public val twoFactorEnabled: Boolean = false,
    @SerialName("hide_subtitles") public val hideSubtitles: Boolean = false,
    @SerialName("dont_autoselect_subtitles") public val dontAutoselectSubtitles: Boolean = false,
    // Defaults mirror the backend: diagnostics opt-out, product analytics opt-in.
    @SerialName("diagnostics_enabled") public val diagnosticsEnabled: Boolean = true,
    @SerialName("product_analytics_enabled") public val productAnalyticsEnabled: Boolean = false,
) {
    @Deprecated("Use useStartFrom; the canonical account setting is use_start_from")
    public val startFrom: Boolean
        get() = useStartFrom
}

@Serializable
public data class AccountInfo(
    @SerialName("user_id") public val userId: Long,
    public val username: String,
    public val mail: String,
    @SerialName("avatar_url") public val avatarUrl: String,
    public val disk: AccountDisk,
    public val settings: AccountSettings,
    @SerialName("account_status") public val accountStatus: String,
    @SerialName("trash_size") public val trashSize: Long = 0,
    @SerialName("account_active") public val accountActive: Boolean? = null,
    @SerialName("download_token") public val downloadToken: AccountDownloadToken? = null,
    public val features: Map<String, Boolean> = emptyMap(),
    @SerialName("files_will_be_deleted_at") public val filesWillBeDeletedAt: String? = null,
    @SerialName("password_last_changed_at") public val passwordLastChangedAt: String? = null,
    @SerialName("user_hash") public val userHash: String? = null,
    /** True for a family plan member; the plan owner pays, so an inactive member cannot renew alone. */
    @SerialName("is_sub_account") public val isSubAccount: Boolean = false,
)

@JvmInline
@Serializable
public value class AccountDownloadToken(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "Download token must not be blank" }
    }

    public override fun toString(): String = "<redacted download token>"
}

public data class AccountInfoQuery(
    public val downloadToken: Boolean = false,
    public val features: Boolean = false,
    public val intercom: Boolean = false,
    public val pas: Boolean = false,
    public val platform: String? = null,
    public val profitwell: Boolean = false,
    public val pushToken: Boolean = false,
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
public data class AccountTwoFactorSettings(
    public val code: String,
    public val enable: Boolean,
) {
    public override fun toString(): String = "AccountTwoFactorSettings(code=<redacted>, enable=$enable)"
}

@Serializable(with = AccountSettingsUpdateSerializer::class)
public sealed interface AccountSettingsUpdate

@Serializable
public data class AccountSettingsPatch(
    @SerialName("history_enabled") public val historyEnabled: Boolean? = null,
    @SerialName("trash_enabled") public val trashEnabled: Boolean? = null,
    @SerialName("hide_subtitles") public val hideSubtitles: Boolean? = null,
    @SerialName("dont_autoselect_subtitles") public val dontAutoselectSubtitles: Boolean? = null,
    @SerialName("tunnel_route_name") public val tunnelRouteName: String? = null,
    @SerialName("show_optimistic_usage") public val showOptimisticUsage: Boolean? = null,
    @SerialName("sort_by") public val sortBy: String? = null,
    @SerialName("use_start_from") public val useStartFrom: Boolean? = null,
    @SerialName("diagnostics_enabled") public val diagnosticsEnabled: Boolean? = null,
    @SerialName("product_analytics_enabled") public val productAnalyticsEnabled: Boolean? = null,
) : AccountSettingsUpdate

@Serializable
public data class AccountUsernameUpdate(
    public val username: String,
) : AccountSettingsUpdate

@Serializable
public data class AccountMailUpdate(
    @SerialName("current_password") public val currentPassword: String,
    public val mail: String,
) : AccountSettingsUpdate {
    public override fun toString(): String = "AccountMailUpdate(currentPassword=<redacted>, mail=$mail)"
}

@Serializable
public data class AccountPasswordUpdate(
    @SerialName("current_password") public val currentPassword: String,
    public val password: String,
) : AccountSettingsUpdate {
    public override fun toString(): String = "AccountPasswordUpdate(currentPassword=<redacted>, password=<redacted>)"
}

@Serializable
public data class AccountTwoFactorUpdate(
    @SerialName("two_factor_enabled") public val twoFactorEnabled: AccountTwoFactorSettings,
) : AccountSettingsUpdate

@Serializable
public data class AccountClearOptions(
    public val files: Boolean = false,
    @SerialName("finished_transfers") public val finishedTransfers: Boolean = false,
    @SerialName("active_transfers") public val activeTransfers: Boolean = false,
    @SerialName("rss_feeds") public val rssFeeds: Boolean = false,
    @SerialName("rss_logs") public val rssLogs: Boolean = false,
    public val history: Boolean = false,
    public val trash: Boolean = false,
    public val friends: Boolean = false,
)

@Serializable
internal data class AccountDestroyInput(
    @SerialName("current_password") val currentPassword: String,
)

public object AccountSettingsUpdateSerializer : KSerializer<AccountSettingsUpdate> {
    public override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AccountSettingsUpdate")

    public override fun serialize(
        encoder: Encoder,
        value: AccountSettingsUpdate,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("AccountSettingsUpdate requires JSON encoding")

        val element =
            when (value) {
                is AccountSettingsPatch -> jsonEncoder.json.encodeToJsonElement(AccountSettingsPatch.serializer(), value)
                is AccountUsernameUpdate -> jsonEncoder.json.encodeToJsonElement(AccountUsernameUpdate.serializer(), value)
                is AccountMailUpdate -> jsonEncoder.json.encodeToJsonElement(AccountMailUpdate.serializer(), value)
                is AccountPasswordUpdate -> jsonEncoder.json.encodeToJsonElement(AccountPasswordUpdate.serializer(), value)
                is AccountTwoFactorUpdate -> jsonEncoder.json.encodeToJsonElement(AccountTwoFactorUpdate.serializer(), value)
            }

        jsonEncoder.encodeJsonElement(element)
    }

    public override fun deserialize(decoder: Decoder): AccountSettingsUpdate {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("AccountSettingsUpdate requires JSON decoding")
        val element = jsonDecoder.decodeJsonElement()
        val jsonObject =
            element as? kotlinx.serialization.json.JsonObject
                ?: throw SerializationException("Expected JSON object for AccountSettingsUpdate")

        return when {
            "two_factor_enabled" in jsonObject -> {
                jsonDecoder.json.decodeFromJsonElement(AccountTwoFactorUpdate.serializer(), element)
            }

            "username" in jsonObject -> {
                jsonDecoder.json.decodeFromJsonElement(AccountUsernameUpdate.serializer(), element)
            }

            "mail" in jsonObject || ("current_password" in jsonObject && "password" !in jsonObject) -> {
                jsonDecoder.json.decodeFromJsonElement(AccountMailUpdate.serializer(), element)
            }

            "password" in jsonObject -> {
                jsonDecoder.json.decodeFromJsonElement(AccountPasswordUpdate.serializer(), element)
            }

            else -> {
                jsonDecoder.json.decodeFromJsonElement(AccountSettingsPatch.serializer(), element)
            }
        }
    }
}
