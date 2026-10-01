# SDK Readiness

How ready `putio-sdk-kotlin` is for autonomous agent work and public package maintenance. Unit tests live under `src/test/kotlin`, the opt-in live suite under `src/liveTest/kotlin`; [Testing](./TESTING.md) has the commands.

## Current Confidence

| Area | Status | Notes |
| --- | --- | --- |
| Package boot | `good` | `./gradlew verify` is stable locally and in CI once Java `21` is available |
| Unit verification | `good` | request shaping, parsing, unknown-value preservation, typed API errors, and localized recovery guidance have deterministic coverage across current domains with a `90%` line floor |
| Live verification | `medium` | account, auth, config, grants, routes, files/trash, uploads, playback-adjacent file helpers, sharing reads, history, and safe transfer read paths are live-covered when credentials are configured; destructive account, share and public-link, and IFTTT mutation paths stay deterministic-only for now |
| Android consumption | `good` | [putio-android](https://github.com/putdotio/putio-android) CI assembles unsigned, R8-minified mobile and TV releases at minSdk 26 through the composite build with no SDK-specific keep rules |
| Release readiness | `good` | tag-driven Maven Central publishing through the Central Portal; `io.put` coordinates, signing, and the `release` environment are wired; the first tag is tracked in [#43](https://github.com/putdotio/putio-sdk-kotlin/issues/43) |

## Highest-Value Next Gaps

1. decide whether IFTTT should grow beyond playback events into a generic event surface
2. deepen deterministic coverage for more conditional payload branches and transport failure paths so the `90%` floor stays comfortable as the surface grows
3. expand live verification as new safe namespaces land, following the shared-account rules
