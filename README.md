<div align="center">
  <p>
    <img src="https://static.put.io/images/putio-boncuk.png" width="72" alt="put.io boncuk">
  </p>

  <h1>putio-sdk-kotlin</h1>

  <p>
    Kotlin SDK for the <a href="https://devs.put.io/api">put.io API</a>
  </p>

  <p>
    <a href="https://github.com/putdotio/putio-sdk-kotlin/actions/workflows/ci.yml?query=branch%3Amain" style="text-decoration:none;"><img src="https://img.shields.io/github/actions/workflow/status/putdotio/putio-sdk-kotlin/ci.yml?branch=main&style=flat&label=ci&colorA=000000&colorB=000000" alt="CI"></a>
    <a href="https://github.com/putdotio/putio-sdk-kotlin/blob/main/LICENSE" style="text-decoration:none;"><img src="https://img.shields.io/github/license/putdotio/putio-sdk-kotlin?style=flat&colorA=000000&colorB=000000" alt="license"></a>
  </p>
</div>

## Installation

```kotlin
dependencies {
    implementation("io.put:putio-sdk-kotlin:<version>")
}
```

The latest version is on [Maven Central](https://central.sonatype.com/artifact/io.put/putio-sdk-kotlin) and in [GitHub releases](https://github.com/putdotio/putio-sdk-kotlin/releases). To build against a checkout instead, use a Gradle composite build with `includeBuild("../putio-sdk-kotlin")`.

## Quick Start

```kotlin
import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig

suspend fun loadAccount() {
    PutioClient(
        PutioConfig(
            accessToken = System.getenv("PUTIO_TOKEN"),
            clientId = "android-app",
            clientName = "put.io Android"
        )
    ).use { sdk ->
        val account = sdk.account.getInfo()
        val rootFiles = sdk.files.list(parentId = 0)

        println(account.username)
        println(rootFiles.files.size)
    }
}
```

## Status

Versioned with [semver](https://semver.org): breaking changes ship only in a major release. Only `main` and the latest release receive fixes.

The surface is smaller than [`putio-sdk-typescript`](https://github.com/putdotio/putio-sdk-typescript) and grows with the put.io mobile and TV apps. [`PutioClient`](./src/main/kotlin/io/putdotio/sdk/PutioClient.kt) lists the namespaces.

## Android Consumers

The SDK emits Java 8 bytecode, leaves the minimum SDK to the consumer, and needs
no SDK-specific R8 keep rules.
[putio-android](https://github.com/putdotio/putio-android) builds minified
releases against it.

OkHttp runs each request and `Dispatchers.Default` decodes its response, so
calls from the main thread do no network or decoding work there. The SDK depends on
`kotlinx-coroutines-core` and does not select `Dispatchers.Main`. Android apps that run their own coroutines on the main
dispatcher must add the Android dispatcher:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<version>")
}
```

Match the `kotlinx-coroutines-core` version in [build.gradle.kts](./build.gradle.kts).

Pass an `OkHttpClient` to add interceptors, caching, or other app policy:

```kotlin
val httpClient = OkHttpClient.Builder()
    .addInterceptor(appInterceptor)
    .cache(Cache(cacheDirectory, 50L * 1024 * 1024))
    .build()

val sdk = PutioClient(
    config = PutioConfig(accessToken = accessToken),
    okHttpClient = httpClient,
)
```

`PutioClient.close()` closes only a default `OkHttpClient` it created, leaving
any injected client open. It creates the default client lazily on the first
HTTP request, so URL builders allocate none.

## Media URLs

Download and stream URL builders take the account download token, never the
access token. Their URLs leave the app for players, cast receivers, and caches:

```kotlin
val downloadToken = sdk.account.getInfo(AccountInfoQuery(downloadToken = true)).downloadToken
    ?: error("account returned no download token")
val url = sdk.files.buildHlsStreamUrl(fileId = file.id, downloadToken = downloadToken)
```

Treat the returned URL as a credential: do not log it or attach it to errors.

`files.get` leaves `streamUrl`, `mp4StreamUrl`, and `mp4Size` `null` by
default, so the API signs no URLs the caller does not read. Opt in per call:

```kotlin
sdk.files.get(fileId, FileDetailsQuery(mp4Size = true, streamUrl = true, mp4StreamUrl = true))
```

A client that sends headers, such as a download cache that persists its URLs,
can keep the token out of the URL. The `*Request` builders return a token-free
URL on `PutioConfig.baseUrl` and the download token as an `Authorization` header:

```kotlin
val request = sdk.files.buildHlsStreamRequest(fileId = file.id, downloadToken = downloadToken, original = true)
dataSource.setDefaultRequestProperties(request.headers)
player.setMediaItem(MediaItem.fromUri(request.url))
```

Persist only `request.url`. Redirect targets and HLS child playlist URLs carry
the token.

A caller that stores the URL before a download token is loaded uses the
matching `*RequestUrl` builder, which returns the same URL, and sends
`Authorization: Token <download token>` on each fetch:

```kotlin
val url = sdk.files.buildDownloadRequestUrl(fileId = file.id)
```

## Playback

`resolvePlayback` returns put.io's converted stream for video unless the app
accepts the original. An `OriginalVideoDecision` gets the file's `mediaInfo`,
or `null` when put.io has not probed it:

```kotlin
val request = PlaybackRequest(
    fileId = file.id,
    mediaCredential = PlaybackMediaCredential.downloadToken(downloadToken),
    preference = PlaybackPreference.HLS,
    useStartFrom = true,
    capabilities = PlaybackCapabilities(
        originalVideoDecision = OriginalVideoDecision { mediaInfo, _, _ ->
            mediaInfo != null && deviceDecodes(mediaInfo)
        },
    ),
)
```

Video streams expose `bitDepth`, `isHdr`, and `dolbyVisionProfile`. For default
audio, use the first audio stream with `isDefault == true`, falling back to
the first audio stream.

An accepted original follows the preference: HLS plays
`/files/{id}/hls/media.m3u8` (with `original=1`), and MP4 plays
`/files/{id}/stream`. The source's `kind` is `ORIGINAL` either way; its
`format` says how to load it.

MP4 never falls back to HLS: a video with no accepted original, no converted
MP4, and nothing to convert resolves as `Unsupported`.

Progressive sources carry sidecar subtitles as `PlaybackSubtitles.Sidecar`.
Mark the track whose `key` matches `defaultKey` as the default; when
`defaultKey` is `null`, put.io named no default, so mark none. put.io ranks
subtitles found beside the file (source `folder`) first, so the default can be
a track outside the requested languages. See
[PlaybackModels.kt](./src/main/kotlin/io/putdotio/sdk/files/PlaybackModels.kt)
for full playback model KDoc.

## Authentication

### OAuth Login URL

```kotlin
val sdk = PutioClient(
    PutioConfig(
        clientId = "android-app",
        clientName = "put.io Android"
    )
)

val loginUrl = sdk.auth.buildLoginUrl(
    redirectUri = "myapp://oauth",
    state = "login"
)
```

### Device-Code Linking (TV)

```kotlin
sdk.deviceCodeAuth.link().collect { state ->
    when (state) {
        is DeviceCodeAuthState.AwaitingLink -> showCode(state.code, state.qrCodeUrl)
        is DeviceCodeAuthState.Linked -> tokenStore.save(state.accessToken)
        is DeviceCodeAuthState.Expired -> offerNewCode()
        is DeviceCodeAuthState.Failed -> showError(PutioErrorLocalizer.localize(state.error))
        DeviceCodeAuthState.Requesting, DeviceCodeAuthState.Validating -> showSpinner()
    }
}
```

One collection is one attempt; collect again for a new code. The SDK owns
polling, timeout, transient network, `429`, and `5xx` retries (honoring
`Retry-After`), and token validation; API errors expose
`PutioApiException.retryAfterSeconds`.

### Tokens and App Icons

- `setAccessToken`, `clearAccessToken`, and `compareAndSetAccessToken` update
  the access token atomically; see
  [PutioClient.kt](./src/main/kotlin/io/putdotio/sdk/PutioClient.kt). Pass a
  token to `auth.logout(token)` to revoke it without changing
  `PutioConfig.accessToken`, or omit it to revoke the configured one.
- `grants.buildIconRequest(appId)` authenticates with `PutioConfig.accessToken`
  in headers so its URL is token-free, and put.io serves an icon only when the
  grant reports `hasIcon == true`; see
  [GrantsApi.kt](./src/main/kotlin/io/putdotio/sdk/grants/GrantsApi.kt).

## Docs

- [Contributing](./CONTRIBUTING.md)
- [Security policy](https://github.com/putdotio/.github/blob/main/SECURITY.md)
- [Agent guide](./AGENTS.md)

## License

This project is available under the [MIT License](./LICENSE).
