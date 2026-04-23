# SDK Readiness

This document tracks how ready `putio-sdk-kotlin` is for autonomous agent work and public package maintenance.

## Overall Status

- deterministic unit verification exists under `src/test/kotlin`
- opt-in live verification exists under `src/liveTest/kotlin`
- `./gradlew verify` now enforces compile, unit-test, jar, and a `90%` line coverage floor
- typed exceptions now feed a user-facing localization layer for recovery guidance
- `./gradlew liveTest` remains the real API verification lane

## Current Confidence

| Area | Status | Notes |
| --- | --- | --- |
| Package boot | `good` | `./gradlew verify` is stable locally and in CI once Java `21` is available |
| Unit verification | `good` | request shaping, parsing, unknown-value preservation, typed API errors, and localized recovery guidance have deterministic coverage across current domains with a `90%` line floor |
| Live verification | `medium` | account, auth, files/trash, and history are live-covered; more namespace depth is still needed as the public surface grows |
| Release readiness | `medium` | local Maven publishing exists, but external package publishing is still intentionally deferred |

## Highest-Value Next Gaps

1. deepen deterministic coverage for more conditional payload branches and transport failure paths so the `90%` floor stays comfortable as the surface grows
2. expand live verification as new namespaces land, following the safe shared-account rules
3. keep narrowing/value-wrapper patterns aligned with the TypeScript SDK as the public contract grows
