package io.putdotio.sdk.grants

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthGrant(
    val id: Long,
    val name: String = "",
    val description: String = "",
    val website: String? = null,
    @SerialName("has_icon") val hasIcon: Boolean? = null,
)

@Serializable
data class OAuthClient(
    val id: Long,
    @SerialName("app_id") val appId: Long,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("client_name") val clientName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    val active: Boolean = false,
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
