package io.putdotio.sdk.files

import io.putdotio.sdk.account.AccountDownloadToken
import io.putdotio.sdk.errors.PutioTransportFailureKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class PlaybackPreference {
    HLS,
    MP4,
}

data class PlaybackCapabilities(
    val originalVideoPlayable: Boolean = false,
)

class PlaybackMediaCredential private constructor(
    internal val value: String,
) {
    override fun equals(other: Any?): Boolean = other is PlaybackMediaCredential && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "<redacted media credential>"

    companion object {
        fun downloadToken(token: AccountDownloadToken): PlaybackMediaCredential = PlaybackMediaCredential(token.value)

        fun downloadToken(value: String): PlaybackMediaCredential {
            require(value.isNotBlank()) { "Download token must not be blank" }
            return PlaybackMediaCredential(value)
        }
    }
}

data class PlaybackRequest(
    val fileId: Long,
    val mediaCredential: PlaybackMediaCredential,
    val preference: PlaybackPreference,
    val useStartFrom: Boolean,
    val capabilities: PlaybackCapabilities = PlaybackCapabilities(),
    val includeSidecarSubtitles: Boolean = true,
    val subtitleLanguages: List<String> = emptyList(),
)

sealed interface PlaybackResolution {
    data class Ready(
        val source: PlaybackSource,
    ) : PlaybackResolution

    data class Conversion(
        val state: PlaybackConversionState,
    ) : PlaybackResolution

    data class Unsupported(
        val fileType: PutioFileType,
    ) : PlaybackResolution
}

enum class PlaybackSourceKind {
    ORIGINAL,
    HLS,
    MP4,
}

data class PlaybackSource(
    val fileId: Long,
    val kind: PlaybackSourceKind,
    val url: PutioCredentialUrl,
    val startFromSeconds: Double,
    val subtitles: PlaybackSubtitles,
)

sealed interface PlaybackSubtitles {
    data object Embedded : PlaybackSubtitles

    data class Sidecar(
        val tracks: List<PlaybackSubtitle>,
    ) : PlaybackSubtitles

    data class Unavailable(
        val failure: PlaybackSubtitleFailure,
    ) : PlaybackSubtitles

    data object None : PlaybackSubtitles
}

sealed interface PlaybackSubtitleFailure {
    data class Rejected(
        val statusCode: Int,
    ) : PlaybackSubtitleFailure

    data class Transport(
        val kind: PutioTransportFailureKind,
    ) : PlaybackSubtitleFailure

    data object InvalidResponse : PlaybackSubtitleFailure
}

data class PlaybackSubtitle(
    val format: String?,
    val key: String,
    val language: String,
    val languageCode: String,
    val name: String,
    val source: String,
    val url: PutioCredentialUrl,
)

sealed interface PlaybackConversionState {
    data object Queued : PlaybackConversionState

    data class Converting(
        val percent: Double?,
    ) : PlaybackConversionState

    data object Completed : PlaybackConversionState

    data object Failed : PlaybackConversionState

    data object NotAvailable : PlaybackConversionState

    data class Unknown(
        val raw: String,
        val percent: Double?,
    ) : PlaybackConversionState
}

class PutioCredentialUrl internal constructor(
    val value: String,
) {
    val encodedPath: String
        get() = parsedUrl().encodedPath

    val queryParameterNames: Set<String>
        get() = parsedUrl().queryParameterNames

    override fun equals(other: Any?): Boolean = other is PutioCredentialUrl && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "<redacted credential URL>"

    private fun parsedUrl() =
        requireNotNull(value.toHttpUrlOrNull()) {
            "Invalid credential URL"
        }
}

@Serializable
internal data class PlaybackFileEnvelope(
    val file: PlaybackFile,
)

@Serializable
internal data class PlaybackFile(
    val id: Long,
    @SerialName("file_type") val fileType: PutioFileType,
    @SerialName("is_mp4_available") val isMp4Available: Boolean? = null,
    @SerialName("need_convert") val needConvert: Boolean? = null,
    @SerialName("start_from") val startFrom: Double? = null,
) {
    init {
        if (fileType == PutioFileType.AUDIO || fileType == PutioFileType.VIDEO) {
            require(startFrom != null && startFrom.isFinite() && startFrom >= 0.0) {
                "start_from must be present, finite, and nonnegative for playable media"
            }
        }
        if (fileType == PutioFileType.VIDEO) {
            require(isMp4Available != null && needConvert != null) {
                "is_mp4_available and need_convert must be present for video playback"
            }
        }
    }
}
