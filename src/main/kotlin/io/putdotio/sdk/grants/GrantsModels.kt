package io.putdotio.sdk.grants

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class OAuthGrant(
    public val id: Long,
    public val name: String = "",
    public val description: String = "",
    public val website: String? = null,
    @SerialName("has_icon") public val hasIcon: Boolean? = null,
)

@Serializable
public data class OAuthClient(
    public val id: Long,
    @SerialName("app_id") public val appId: Long,
    @SerialName("app_name") public val appName: String? = null,
    @SerialName("client_name") public val clientName: String = "",
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("last_used_at") public val lastUsedAt: String? = null,
    public val active: Boolean = false,
)

@Serializable
internal data class GrantsEnvelope(
    val apps: List<OAuthGrant> = emptyList(),
    val status: String,
)

@Serializable
internal data class GrantEnvelope(
    val app: OAuthGrant,
    val status: String,
)

@Serializable
internal data class ClientsEnvelope(
    val clients: List<OAuthClient> = emptyList(),
    val status: String,
)
