<div align="center">
  <p>
    <img src="https://static.put.io/images/putio-boncuk.png" width="72" alt="put.io boncuk">
  </p>

  <h1>putio-sdk-kotlin</h1>

  <p>
    Kotlin SDK for the <a href="https://api.put.io/v2/docs">put.io API</a>
  </p>

  <p>
    Domain-first, coroutine-friendly, and parsed at the boundary.
  </p>

  <p>
    <a href="https://github.com/putdotio/putio-sdk-kotlin/actions/workflows/ci.yml?query=branch%3Amain" style="text-decoration:none;"><img src="https://img.shields.io/github/actions/workflow/status/putdotio/putio-sdk-kotlin/ci.yml?branch=main&style=flat&label=ci&colorA=000000&colorB=000000" alt="CI"></a>
    <a href="https://github.com/putdotio/putio-sdk-kotlin/blob/main/LICENSE" style="text-decoration:none;"><img src="https://img.shields.io/github/license/putdotio/putio-sdk-kotlin?style=flat&colorA=000000&colorB=000000" alt="license"></a>
  </p>
</div>

## Status

This repository is in its first bootstrap phase. The initial public surface is intentionally smaller than `putio-sdk-typescript` and currently focuses on:

- `account`
- `auth`
- `appConfig`
- `files`
- `grants`
- `history`
- `ifttt`
- `routes`
- `trash`
- `transfers`

The design goal is to stay closer to the TypeScript SDK than the legacy Swift SDK: coroutine-first APIs, typed errors, explicit boundary parsing, forward-compatible value types at the API edge, and a user-facing error localization layer for recovery guidance.

The current expansion is shaped around real put.io mobile and TV app needs. The primary consumer is the sibling [putio-android](https://github.com/putdotio/putio-android) app, which uses this SDK as its API boundary through a Gradle composite build; the covered flows mirror what the put.io mobile and TV apps use, especially:

- auth login, device/OOB, token validation, and two-factor flows
- OAuth grant listing, revocation, logout, and device linking
- account settings updates for playback, sorting, and trash/history preferences
- app-scoped config and tunnel route reads for playback and network preferences
- file listing/search, cursor continuation, subtitles, file management, next-media lookup, MP4 conversion, and playback resume helpers
- typed HLS/MP4/original playback resolution using an app-supplied preference and account download token
- history event listing and deletion flows
- IFTTT playback event sending
- trash listing, cursor continuation, restore, delete, and empty flows
- transfer listing, cursor continuation, add/cancel/clean/retry, count, and info flows

## Installation

The first public Maven release is not wired yet.

Until then, the repo supports local consumption through:

```bash
./gradlew publishToMavenLocal
```

## Android Consumers

The SDK emits Java 8 bytecode and leaves the Android minimum SDK to the
consumer. The first-party Android app has validated composite-build
consumption at minSdk 26 with an unsigned, R8-minified release build. The
current SDK, OkHttp, coroutines, and serialization stack needs no
SDK-specific consumer keep rules.

The SDK depends on `kotlinx-coroutines-core` and does not select
`Dispatchers.Main`. Android apps that run their own coroutines on the main
dispatcher must add the Android dispatcher:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
```

Pass an `OkHttpClient` to add application interceptors, caching, or other
consumer policy:

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

An injected client remains caller-owned: `PutioClient.close()` does not close
its cache, dispatcher, or connection pool. A client created internally by
`PutioClient` is closed with the SDK.

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

## Authentication URL Example

```kotlin
val sdk = PutioClient(
    PutioConfig(
        clientId = "android-app",
        clientName = "put.io Android"
    )
)

val loginUrl = sdk.auth.buildLoginUrl(
    redirectUri = "putio://auth",
    state = "android-login"
)
```

## Verification

The repo exposes one canonical verification command:

```bash
./gradlew verify
./gradlew liveTest
```

The repository targets JDK `21`. Install a Java `21` runtime with your preferred version manager or system package manager before running Gradle. The checked-in [.java-version](./.java-version) is only a compatibility hint for tools that choose to honor it.

This currently runs compile, format, and test guardrails for the bootstrap surface. Tests use `MockWebServer` to verify request shaping, auth handling, and response decoding. `./gradlew verify` also runs `spotlessCheck` (stock ktlint rules) and enforces a `90%` line coverage floor for the current source set. Fix formatting findings with `./gradlew spotlessApply`.

An opt-in live suite is also available through `./gradlew liveTest`. It follows the TypeScript SDK convention of keeping real API verification separate from the default unit suite.

## Docs

- [Architecture](./docs/ARCHITECTURE.md)
- [Testing](./docs/TESTING.md)
- [Readiness](./docs/READINESS.md)
- [Release notes](./docs/RELEASE.md)
- [Agent guide](./AGENTS.md)

## License

This project is available under the [MIT License](./LICENSE)
