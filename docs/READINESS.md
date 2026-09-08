# SDK Readiness

This document tracks how ready `putio-sdk-kotlin` is for autonomous agent work and public package maintenance.

## Overall Status

- deterministic unit verification exists under `src/test/kotlin`
- opt-in live verification exists under `src/liveTest/kotlin`
- `./gradlew verify` now enforces compile, unit-test, jar, and a `90%` line coverage floor
- the first-party Android app's CI builds an R8-minified minSdk 26 release through the composite SDK on every change
- typed exceptions now feed a user-facing localization layer for recovery guidance
- `deviceCodeAuth.link()` orchestrates TV-style linking as a flow with a redacted `Linked` state
- files, trash, move, and delete envelopes reject non-OK HTTP 2xx bodies at the boundary
- `./gradlew liveTest` remains the real API verification lane

## Current Confidence

| Area | Status | Notes |
| --- | --- | --- |
| Package boot | `good` | `./gradlew verify` is stable locally and in CI once Java `21` is available |
| Unit verification | `good` | request shaping, parsing, unknown-value preservation, typed API errors, and localized recovery guidance have deterministic coverage across current domains with a `90%` line floor |
| Live verification | `medium` | account, auth, config, grants, routes, files/trash, playback-adjacent file helpers, history, and safe transfer read paths are live-covered when credentials are configured; destructive account and IFTTT mutation paths stay deterministic-only for now |
| Android consumption | `good` | [putio-android](https://github.com/putdotio/putio-android) CI assembles an unsigned, R8-minified mobile release at minSdk 26 through the composite build with no SDK-specific keep rules |
| Release readiness | `good` | tag-driven Maven Central publishing through the Central Portal; `io.put` coordinates, signing, and the `release` environment are wired; the first tag is tracked in [#43](https://github.com/putdotio/putio-sdk-kotlin/issues/43)) |

## Highest-Value Next Gaps

1. decide whether IFTTT should grow beyond playback events into a generic event surface (the device-code orchestrator for TV auth landed in #45)
2. deepen deterministic coverage for more conditional payload branches and transport failure paths so the `90%` floor stays comfortable as the surface grows
3. expand live verification as new safe namespaces land, following the shared-account rules
