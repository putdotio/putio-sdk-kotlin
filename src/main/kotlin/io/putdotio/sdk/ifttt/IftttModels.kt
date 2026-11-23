package io.putdotio.sdk.ifttt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class IftttPlaybackEventInput(
    @SerialName("event_type") public val eventType: String,
    public val ingredients: IftttPlaybackEventIngredients,
)

@Serializable
public data class IftttPlaybackEventIngredients(
    @SerialName("file_id") public val fileId: Long,
    @SerialName("file_name") public val fileName: String,
    @SerialName("file_type") public val fileType: String,
)
