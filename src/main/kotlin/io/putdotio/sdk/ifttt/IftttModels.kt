package io.putdotio.sdk.ifttt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IftttPlaybackEventInput(
    @SerialName("event_type") val eventType: String,
    val ingredients: IftttPlaybackEventIngredients,
)

@Serializable
data class IftttPlaybackEventIngredients(
    @SerialName("file_id") val fileId: Long,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_type") val fileType: String,
)
