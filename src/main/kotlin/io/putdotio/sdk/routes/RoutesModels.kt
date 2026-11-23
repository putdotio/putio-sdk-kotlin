package io.putdotio.sdk.routes

import kotlinx.serialization.Serializable

@Serializable
public data class TunnelRoute(
    public val name: String = "",
    public val description: String = "",
    public val hosts: List<String> = emptyList(),
)

@Serializable
internal data class RoutesEnvelope(
    val routes: List<TunnelRoute> = emptyList(),
    val status: String,
)
