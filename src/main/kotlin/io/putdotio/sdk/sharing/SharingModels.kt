package io.putdotio.sdk.sharing

import io.putdotio.sdk.files.PutioFile
import io.putdotio.sdk.files.PutioFileType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Who receives the files passed to [SharingApi.shareFiles]. */
sealed interface ShareTarget {
    data object Everyone : ShareTarget

    data class Friends(
        val friendNames: List<String>,
    ) : ShareTarget {
        init {
            require(friendNames.isNotEmpty()) { "Friends share target requires at least one friend name" }
            require(friendNames.none { it.isBlank() }) { "Friend names must not be blank" }
        }
    }
}

data class ShareFilesInput(
    val target: ShareTarget,
    val ids: List<Long> = emptyList(),
    val cursor: String? = null,
    val excludeIds: List<Long> = emptyList(),
) {
    init {
        require(ids.isNotEmpty() || !cursor.isNullOrBlank()) {
            "ShareFilesInput requires either ids or cursor"
        }
    }
}

internal fun ShareFilesInput.toFormMap(): Map<String, String> =
    buildMap {
        cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        if (excludeIds.isNotEmpty()) put("exclude_ids", excludeIds.joinToString(","))
        if (ids.isNotEmpty()) put("file_ids", ids.joinToString(","))
        put(
            "friends",
            when (target) {
                ShareTarget.Everyone -> "everyone"
                is ShareTarget.Friends -> target.friendNames.joinToString(",")
            },
        )
    }

/** The `shared_with` summary on a shared-files entry. */
sealed interface SharedFileAudience {
    data object Everyone : SharedFileAudience

    data class Friends(
        val count: Int,
    ) : SharedFileAudience

    /** A backend value this SDK version does not know, kept verbatim. */
    data class Unknown(
        val raw: String,
    ) : SharedFileAudience
}

@Serializable(with = SharedFile.Serializer::class)
data class SharedFile(
    val file: PutioFile,
    val sharedWith: SharedFileAudience,
) {
    internal object Serializer : KSerializer<SharedFile> {
        override val descriptor = buildClassSerialDescriptor("SharedFile")

        override fun deserialize(decoder: Decoder): SharedFile {
            val jsonDecoder =
                decoder as? JsonDecoder
                    ?: throw SerializationException("SharedFile requires JSON decoding")
            val element = jsonDecoder.decodeJsonElement()
            val sharedWith =
                element.jsonObject["shared_with"] as? JsonPrimitive
                    ?: throw SerializationException("Shared file requires a shared_with value")

            return SharedFile(
                file = jsonDecoder.json.decodeFromJsonElement(PutioFile.serializer(), element),
                sharedWith = sharedWith.toAudience(),
            )
        }

        override fun serialize(
            encoder: Encoder,
            value: SharedFile,
        ) {
            val jsonEncoder =
                encoder as? JsonEncoder
                    ?: throw SerializationException("SharedFile requires JSON encoding")
            val file = jsonEncoder.json.encodeToJsonElement(PutioFile.serializer(), value.file).jsonObject
            jsonEncoder.encodeJsonElement(
                buildJsonObject {
                    file.forEach { (key, element) -> put(key, element) }
                    put(
                        "shared_with",
                        when (val audience = value.sharedWith) {
                            SharedFileAudience.Everyone -> JsonPrimitive("everyone")
                            is SharedFileAudience.Friends -> JsonPrimitive(audience.count)
                            is SharedFileAudience.Unknown -> JsonPrimitive(audience.raw)
                        },
                    )
                },
            )
        }

        private fun JsonPrimitive.toAudience(): SharedFileAudience =
            when {
                isString && content == "everyone" -> {
                    SharedFileAudience.Everyone
                }

                isString -> {
                    SharedFileAudience.Unknown(content)
                }

                else -> {
                    val count =
                        intOrNull?.takeIf { it >= 0 }
                            ?: throw SerializationException("shared_with must be \"everyone\" or a nonnegative count")
                    SharedFileAudience.Friends(count)
                }
            }
    }
}

@Serializable
data class SharedFileShare(
    @SerialName("share_id") val shareId: Long,
    @SerialName("user_name") val userName: String,
    @SerialName("user_avatar_url") val userAvatarUrl: String,
)

/** Who a single file is shared with, from `GET /files/{id}/shared-with-v2`. */
sealed interface SharedWith {
    data object Everyone : SharedWith

    data class Friends(
        val shares: List<SharedFileShare>,
    ) : SharedWith

    /** A backend share type this SDK version does not know, kept verbatim. */
    data class Unknown(
        val shareType: String,
    ) : SharedWith
}

@Serializable
internal data class SharedWithEnvelope(
    @SerialName("share_type") val shareType: String,
    val shares: List<SharedFileShare>? = null,
    val status: String? = null,
) {
    init {
        require(status == null || status == "OK") { "Shared-with response status must be OK" }
        require(shareType != "friends" || shares != null) { "Friends share type requires shares" }
    }

    fun toSharedWith(): SharedWith =
        when (shareType) {
            "everyone" -> SharedWith.Everyone
            "friends" -> SharedWith.Friends(shares.orEmpty())
            else -> SharedWith.Unknown(shareType)
        }
}

@Serializable
internal data class SharedFilesEnvelope(
    val shared: List<SharedFile>,
    val status: String,
) {
    init {
        require(status == "OK") { "Shared files response status must be OK" }
    }
}

/** Bearer secret of a public link; anyone holding it can read the shared file. */
@JvmInline
@Serializable
value class PublicShareToken(
    val value: String,
) {
    override fun toString(): String = "<redacted public share token>"
}

@Serializable
data class PublicShareOwner(
    val name: String,
)

@Serializable
data class PublicShareFile(
    val id: Long,
    val name: String,
    @SerialName("file_type") val fileType: PutioFileType,
)

@Serializable
data class PublicShare(
    val id: Long,
    val token: PublicShareToken,
    @SerialName("push_token") val pushToken: PublicShareToken,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expiration_date") val expirationDate: String,
    val owner: PublicShareOwner,
    @SerialName("user_file") val userFile: PublicShareFile,
)

@Serializable
internal data class PublicShareEnvelope(
    @SerialName("public_share") val publicShare: PublicShare,
    val status: String? = null,
) {
    init {
        require(status == null || status == "OK") { "Public share response status must be OK" }
    }
}

@Serializable
internal data class PublicSharesEnvelope(
    @SerialName("public_shares") val publicShares: List<PublicShare>,
    val status: String? = null,
) {
    init {
        require(status == null || status == "OK") { "Public shares response status must be OK" }
    }
}
