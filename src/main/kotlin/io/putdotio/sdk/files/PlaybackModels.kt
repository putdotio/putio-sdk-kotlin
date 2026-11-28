package io.putdotio.sdk.files

import io.putdotio.sdk.account.AccountDownloadToken
import io.putdotio.sdk.errors.PutioTransportFailureKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

public enum class PlaybackPreference {
    HLS,
    MP4,
}

/**
 * Decides whether this device can play a video's original file. `null` [mediaInfo] means put.io
 * has not probed the file.
 */
public fun interface OriginalVideoDecision {
    public fun canPlayOriginal(
        mediaInfo: PutioMediaInfo?,
        needConvert: Boolean,
        isMp4Available: Boolean,
    ): Boolean
}

/**
 * [originalVideoPlayable] plays every original video without asking [originalVideoDecision].
 * With neither, video resolves to put.io's converted stream.
 */
public data class PlaybackCapabilities(
    public val originalVideoPlayable: Boolean = false,
    public val originalVideoDecision: OriginalVideoDecision? = null,
)

public class PlaybackMediaCredential private constructor(
    internal val value: String,
) {
    public override fun equals(other: Any?): Boolean = other is PlaybackMediaCredential && value == other.value

    public override fun hashCode(): Int = value.hashCode()

    public override fun toString(): String = "<redacted media credential>"

    public companion object {
        public fun downloadToken(token: AccountDownloadToken): PlaybackMediaCredential = PlaybackMediaCredential(token.value)

        public fun downloadToken(value: String): PlaybackMediaCredential {
            require(value.isNotBlank()) { "Download token must not be blank" }
            return PlaybackMediaCredential(value)
        }
    }
}

public data class PlaybackRequest(
    public val fileId: Long,
    public val mediaCredential: PlaybackMediaCredential,
    public val preference: PlaybackPreference,
    public val useStartFrom: Boolean,
    public val capabilities: PlaybackCapabilities = PlaybackCapabilities(),
    public val includeSidecarSubtitles: Boolean = true,
    public val subtitleLanguages: List<String> = emptyList(),
    /** HLS only; see [FilesApi.buildHlsStreamUrl]. */
    public val maxSubtitleCount: Int? = null,
) {
    init {
        requireValidMaxSubtitleCount(maxSubtitleCount)
    }
}

/** `max_subtitle_count` value that asks the HLS master for every subtitle rendition. */
public const val HLS_ALL_SUBTITLES: Int = -1

internal fun requireValidMaxSubtitleCount(maxSubtitleCount: Int?) {
    require(maxSubtitleCount == null || maxSubtitleCount >= HLS_ALL_SUBTITLES) {
        "maxSubtitleCount must be HLS_ALL_SUBTITLES (-1), 0, or a positive count"
    }
}

public sealed interface PlaybackResolution {
    public data class Ready(
        public val source: PlaybackSource,
    ) : PlaybackResolution

    public data class Conversion(
        public val state: PlaybackConversionState,
    ) : PlaybackResolution

    public data class Unsupported(
        public val fileType: PutioFileType,
    ) : PlaybackResolution
}

public enum class PlaybackSourceKind {
    ORIGINAL,
    HLS,
    MP4,
}

public enum class PlaybackStreamFormat {
    HLS,
    PROGRESSIVE,
}

/**
 * [kind] says what is played and [format] how it is delivered: an original video under the HLS
 * preference is [PlaybackSourceKind.ORIGINAL] over [PlaybackStreamFormat.HLS].
 */
public data class PlaybackSource(
    public val fileId: Long,
    public val kind: PlaybackSourceKind,
    public val url: PutioCredentialUrl,
    public val startFromSeconds: Double,
    public val subtitles: PlaybackSubtitles,
    public val format: PlaybackStreamFormat =
        if (kind == PlaybackSourceKind.HLS) PlaybackStreamFormat.HLS else PlaybackStreamFormat.PROGRESSIVE,
    public val mediaInfo: PutioMediaInfo? = null,
)

public sealed interface PlaybackSubtitles {
    public data object Embedded : PlaybackSubtitles

    /**
     * [defaultKey] is the [PlaybackSubtitle.key] of the track put.io names as the list's default;
     * null when the list names none of [tracks]. put.io ranks subtitles found beside the file
     * (source `folder`, often language `und`) ahead of the tracks that match
     * [PlaybackRequest.subtitleLanguages] or the account's subtitle languages, so the default can
     * be a track in none of those languages.
     */
    public data class Sidecar(
        public val tracks: List<PlaybackSubtitle>,
        public val defaultKey: String? = null,
    ) : PlaybackSubtitles

    public data class Unavailable(
        public val failure: PlaybackSubtitleFailure,
    ) : PlaybackSubtitles

    public data object None : PlaybackSubtitles
}

public sealed interface PlaybackSubtitleFailure {
    public data class Rejected(
        public val statusCode: Int,
    ) : PlaybackSubtitleFailure

    public data class Transport(
        public val kind: PutioTransportFailureKind,
    ) : PlaybackSubtitleFailure

    public data object InvalidResponse : PlaybackSubtitleFailure
}

public data class PlaybackSubtitle(
    public val format: String?,
    public val key: String,
    public val language: String,
    public val languageCode: String,
    public val name: String,
    public val source: String,
    public val url: PutioCredentialUrl,
)

public sealed interface PlaybackConversionState {
    public data object Queued : PlaybackConversionState

    public data class Converting(
        public val percent: Double?,
    ) : PlaybackConversionState

    public data object Completed : PlaybackConversionState

    public data object Failed : PlaybackConversionState

    /**
     * The file needs conversion but none has been requested. Not terminal:
     * [FilesApi.startMp4Conversion] moves it to [Queued] or [Converting].
     */
    public data object NotAvailable : PlaybackConversionState

    public data class Unknown(
        public val raw: String,
        public val percent: Double?,
    ) : PlaybackConversionState
}

public class PutioCredentialUrl internal constructor(
    public val value: String,
) {
    private val parsed: HttpUrl by lazy {
        requireNotNull(value.toHttpUrlOrNull()) { "Invalid credential URL" }
    }

    public val encodedPath: String
        get() = parsed.encodedPath

    public val queryParameterNames: Set<String>
        get() = parsed.queryParameterNames

    public override fun equals(other: Any?): Boolean = other is PutioCredentialUrl && value == other.value

    public override fun hashCode(): Int = value.hashCode()

    public override fun toString(): String = "<redacted credential URL>"

    public companion object {
        /**
         * Wraps a media URL the consumer already holds under the redaction contract,
         * for example a downloaded rendition it replays through its own cache.
         * Validates once; the internal constructor keeps the resolver's lazy contract.
         */
        public fun of(value: String): PutioCredentialUrl = PutioCredentialUrl(value).also { it.parsed }
    }
}

/**
 * A media request that authenticates in [headers], so [url] carries no token and is safe to
 * persist or log. Redirect targets and HLS child playlist URLs carry the token, so persist only
 * [url]. [headers] hold the token the request was built with.
 */
public class PutioMediaRequest internal constructor(
    public val url: String,
    public val headers: Map<String, String>,
) {
    public override fun equals(other: Any?): Boolean = other is PutioMediaRequest && url == other.url && headers == other.headers

    public override fun hashCode(): Int = 31 * url.hashCode() + headers.hashCode()

    public override fun toString(): String = "PutioMediaRequest(url=$url, headers=<redacted>)"
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
    @SerialName("media_info") val mediaInfo: PutioMediaInfo? = null,
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
