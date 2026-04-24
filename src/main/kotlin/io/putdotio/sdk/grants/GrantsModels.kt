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
internal data class GrantsEnvelope(
    val apps: List<OAuthGrant> = emptyList(),
    val status: String,
)

@Serializable
internal data class GrantEnvelope(
    val app: OAuthGrant,
    val status: String,
)
