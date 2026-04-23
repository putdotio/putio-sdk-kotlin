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
- `files`
- `history`
- `trash`

The design goal is to stay closer to the TypeScript SDK than the legacy Swift SDK: coroutine-first APIs, typed errors, explicit boundary parsing, forward-compatible value types at the API edge, and a user-facing error localization layer for recovery guidance.

The current expansion is shaped around the real mobile and TV app surfaces in `apps/ios` and `apps/web/apps/tv-native`, especially:

- account settings updates for playback and trash/history preferences
- file listing/search, cursor continuation, subtitles, and playback resume helpers
- history event listing and deletion flows
- trash listing, cursor continuation, restore, delete, and empty flows

## Installation

The first public Maven release is not wired yet.

Until then, the repo supports local consumption through:

```bash
./gradlew publishToMavenLocal
```

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
    redirectUri = "putio://auth/callback",
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

This currently runs compile and test guardrails for the bootstrap surface. Tests use `MockWebServer` to verify request shaping, auth handling, and response decoding. `./gradlew verify` also enforces a `90%` line coverage floor for the current source set.

An opt-in live suite is also available through `./gradlew liveTest`. It follows the TypeScript SDK convention of keeping real API verification separate from the default unit suite.

## Docs

- [Architecture](./docs/ARCHITECTURE.md)
- [Testing](./docs/TESTING.md)
- [Readiness](./docs/READINESS.md)
- [Release notes](./docs/RELEASE.md)
- [Agent guide](./AGENTS.md)

## License

This project is available under the [MIT License](./LICENSE)
