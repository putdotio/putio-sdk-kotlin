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

Stable since 1.0.0 and versioned with [semver](https://semver.org): breaking changes ship only in a major release. The public surface is deliberately smaller than [`putio-sdk-typescript`](https://github.com/putdotio/putio-sdk-typescript); [Architecture](./docs/ARCHITECTURE.md#current-namespace-scope) lists the namespaces and operations.

The surface grows with the put.io mobile and TV apps. The primary consumer is [putio-android](https://github.com/putdotio/putio-android), which uses this SDK as its API boundary and pins the Maven Central release.

Only `main` and the latest `io.put:putio-sdk-kotlin` release receive fixes.

## Android Consumers

The SDK emits Java 8 bytecode and leaves the Android minimum SDK to the
consumer. The first-party Android app's CI builds unsigned, R8-minified
releases at minSdk 26 against the Maven Central release. The
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

## Media URLs

Download and stream URL builders take the account download token, never the
access token, because their URLs leave the app for players, cast receivers,
and caches:

```kotlin
val downloadToken = sdk.account.getInfo(AccountInfoQuery(downloadToken = true)).downloadToken
    ?: error("account returned no download token")
val url = sdk.files.buildHlsStreamUrl(fileId = file.id, downloadToken = downloadToken)
```

Treat the returned URL as a credential: do not log it or attach it to errors.

## Device-Code Linking (TV)

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

One collection is one attempt; collect again for a new code. Polling, timeout, and
token validation live in the SDK; see the [device-code contract](./docs/ARCHITECTURE.md#device-code-contract).

## Authentication URL Example

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

## Verification

```bash
./gradlew verify
./gradlew liveTest
```

Both need a Java `21` runtime, and `verify` also needs Node for its Markdown check. `verify` is the deterministic gate; `liveTest` is opt-in against the real put.io API and needs credentials. [Testing](./docs/TESTING.md) covers both.

## Docs

- [Architecture](./docs/ARCHITECTURE.md)
- [Testing](./docs/TESTING.md)
- [Readiness](./docs/READINESS.md)
- [Distribution](./docs/DISTRIBUTION.md)
- [Security policy](https://github.com/putdotio/.github/blob/main/SECURITY.md)
- [Agent guide](./AGENTS.md)

## License

This project is available under the [MIT License](./LICENSE)
