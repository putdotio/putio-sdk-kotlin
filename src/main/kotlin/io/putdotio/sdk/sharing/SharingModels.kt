package io.putdotio.sdk.sharing

import io.putdotio.sdk.core.RawStringValueSerializer
import io.putdotio.sdk.errors.redactSensitiveUrlsInText
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
public sealed interface ShareTarget {
    public data object Everyone : ShareTarget

    public data class Friends(
        public val friendNames: List<String>,
    ) : ShareTarget {
        init {
            require(friendNames.isNotEmpty()) { "Friends share target requires at least one friend name" }
            require(friendNames.none { it.isBlank() }) { "Friend names must not be blank" }
            // The API reads the comma-joined `friends` field, where `everyone` means a public share.
            require(friendNames.none { ',' in it }) { "Friend names must not contain commas" }
            require(friendNames.none { it.trim().equals("everyone", ignoreCase = true) }) {
                "Use ShareTarget.Everyone to share with everyone"
            }
        }
    }
}

public data class ShareFilesInput(
    public val target: ShareTarget,
    public val ids: List<Long> = emptyList(),
    public val cursor: String? = null,
    public val excludeIds: List<Long> = emptyList(),
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

/**
 * Items shared with the viewer to copy into their own [parentId] folder (root is `0`). Copying
 * runs in the background; poll [SharingApi.getCloneInfo] with the id [SharingApi.cloneSharedFiles]
 * returns.
 */
public data class CloneSharedFilesInput(
    public val ids: List<Long> = emptyList(),
    public val cursor: String? = null,
    public val excludeIds: List<Long> = emptyList(),
    public val parentId: Long = 0,
) {
    init {
        require(ids.isNotEmpty() || !cursor.isNullOrBlank()) { "CloneSharedFilesInput requires either ids or cursor" }
        require(ids.all { it > 0 } && excludeIds.all { it > 0 }) { "Clone file ids must be positive" }
        require(parentId >= 0) { "Clone parent id must not be negative" }
    }
}

internal fun CloneSharedFilesInput.toFormMap(): Map<String, String> =
    buildMap {
        cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        if (excludeIds.isNotEmpty()) put("exclude_ids", excludeIds.joinToString(","))
        if (ids.isNotEmpty()) put("file_ids", ids.joinToString(","))
        put("parent_id", parentId.toString())
    }

@Serializable
internal data class CloneSharedFilesEnvelope(
    val id: Long,
    val status: String? = null,
) {
    init {
        require(id > 0) { "Clone id must be positive" }
        require(status == null || status == "OK") { "Clone response status must be OK" }
    }
}

@Serializable(with = SharedFileCloneStatus.Serializer::class)
@JvmInline
public value class SharedFileCloneStatus(
    public val raw: String,
) {
    public val isKnown: Boolean
        get() = this in knownValues

    /** put.io stops working on the copy in these states; any other status may still change. */
    public val isFinished: Boolean
        get() = this == DONE || this == ERROR

    public override fun toString(): String = raw

    public companion object {
        public val NEW: SharedFileCloneStatus = SharedFileCloneStatus("NEW")
        public val PROCESSING: SharedFileCloneStatus = SharedFileCloneStatus("PROCESSING")
        public val DONE: SharedFileCloneStatus = SharedFileCloneStatus("DONE")
        public val ERROR: SharedFileCloneStatus = SharedFileCloneStatus("ERROR")

        private val knownValues = setOf(NEW, PROCESSING, DONE, ERROR)

        public fun fromRaw(raw: String): SharedFileCloneStatus = knownValues.firstOrNull { it.raw == raw } ?: SharedFileCloneStatus(raw)
    }

    public object Serializer : RawStringValueSerializer<SharedFileCloneStatus>("SharedFileCloneStatus") {
        override fun fromRaw(raw: String): SharedFileCloneStatus = Companion.fromRaw(raw)

        override fun toRaw(value: SharedFileCloneStatus): String = value.raw
    }
}

/**
 * A background copy started by [SharingApi.cloneSharedFiles]; [errorMessage] is put.io's English reason for
 * [SharedFileCloneStatus.ERROR], redacted like [io.putdotio.sdk.errors.PutioApiException.errorMessage].
 */
public data class SharedFileCloneInfo(
    public val status: SharedFileCloneStatus,
    public val errorMessage: String? = null,
)

@Serializable
internal data class SharedFileCloneInfoEnvelope(
    @SerialName("shared_file_clone_status") val cloneStatus: SharedFileCloneStatus,
    @SerialName("error_msg") val errorMessage: String? = null,
    val status: String? = null,
) {
    init {
        require(status == null || status == "OK") { "Clone info response status must be OK" }
    }

    fun toInfo(): SharedFileCloneInfo = SharedFileCloneInfo(cloneStatus, errorMessage?.let(::redactSensitiveUrlsInText))
}

/** The `shared_with` summary on a shared-files entry. */
public sealed interface SharedFileAudience {
    public data object Everyone : SharedFileAudience

    public data class Friends(
        public val count: Int,
    ) : SharedFileAudience

    /** A backend value this SDK version does not know, kept verbatim. */
    public data class Unknown(
        public val raw: String,
    ) : SharedFileAudience
}

@Serializable(with = SharedFile.Serializer::class)
public data class SharedFile(
    public val file: PutioFile,
    public val sharedWith: SharedFileAudience,
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
public data class SharedFileShare(
    @SerialName("share_id") public val shareId: Long,
    @SerialName("user_name") public val userName: String,
    @SerialName("user_avatar_url") public val userAvatarUrl: String,
)

/** Who a single file is shared with, from `GET /files/{id}/shared-with-v2`. */
public sealed interface SharedWith {
    public data object Everyone : SharedWith

    public data class Friends(
        public val shares: List<SharedFileShare>,
    ) : SharedWith

    /** A backend share type this SDK version does not know, kept verbatim. */
    public data class Unknown(
        public val shareType: String,
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
public value class PublicShareToken(
    public val value: String,
) {
    public override fun toString(): String = "<redacted public share token>"
}

@Serializable
public data class PublicShareOwner(
    public val name: String,
)

@Serializable
public data class PublicShareFile(
    public val id: Long,
    public val name: String,
    @SerialName("file_type") public val fileType: PutioFileType,
)

@Serializable
public data class PublicShare(
    public val id: Long,
    public val token: PublicShareToken,
    @SerialName("push_token") public val pushToken: PublicShareToken,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("expiration_date") public val expirationDate: String,
    public val owner: PublicShareOwner,
    @SerialName("user_file") public val userFile: PublicShareFile,
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
