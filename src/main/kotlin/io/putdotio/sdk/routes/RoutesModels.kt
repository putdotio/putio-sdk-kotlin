package io.putdotio.sdk.routes

import kotlinx.serialization.Serializable

@Serializable
data class TunnelRoute(
    val name: String = "",
    val description: String = "",
    val hosts: List<String> = emptyList(),
)

@Serializable
internal data class RoutesEnvelope(
    val routes: List<TunnelRoute> = emptyList(),
    val status: String,
)
