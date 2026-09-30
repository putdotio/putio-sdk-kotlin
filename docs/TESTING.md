# Testing

## Commands

```bash
./gradlew test
./gradlew verify
./gradlew liveTest
```

Install a Java `21` runtime before running these commands. The checked-in [.java-version](../.java-version) is only a compatibility hint for tools that choose to honor it.

## Current Verification Shape

- `./gradlew test` runs the unit suite; request and response behavior is exercised with `MockWebServer`, including the localized user-facing error mapping layer
- `./gradlew verify` is the canonical guardrail: `check` (test plus `spotlessCheck` with stock ktlint rules; `./gradlew spotlessApply` fixes findings), `jar`, and the `90%` line coverage floor defined in [build.gradle.kts](../build.gradle.kts)
- [ci.yml](../.github/workflows/ci.yml) runs the same `verify` lane on `ubuntu-24.04-arm` with Temurin 21
- `./gradlew liveTest` runs the opt-in live suite against the real put.io API; it is excluded from `test` and `verify`
- JaCoCo HTML and XML reports are written under `build/reports/jacoco/test`

## Live Environment

Copy [.env.example](../.env.example) when using your own credentials. Supported environment variables:

- `PUTIO_TOKEN_FIRST_PARTY` (`PUTIO_ACCESS_TOKEN` and `PUTIO_TOKEN` are accepted fallbacks)
- `PUTIO_CLIENT_ID`
- `PUTIO_PLAYBACK_FIXTURE_ID` (stable owned, converted video in the dedicated profile)
- `PUTIO_BASE_URL` (optional, defaults to `https://api.put.io/v2/`)

Run `make secrets-setup` with `PUTIO_SDK_KOTLIN_SOPS_FILE` pointing to the
maintainer-supplied SOPS ciphertext. The command requires SOPS 3.10 or newer
and `jq`, rejects plaintext or malformed payloads, and writes owner-only
`.env.local`. The live harness auto-loads `.env.local` and `.env`;
already-exported environment variables keep highest priority. Run
`make secrets-clean` before removing the worktree.

## Live Scope

Current live targets cover:

- token validation, OOB auth-code fetch, and the device-code orchestrator reaching `AwaitingLink` then `Expired` on a one-poll budget
- account info and reversible account settings mutation
- disposable file create, search, trash restore, and cleanup flows
- playback source resolution, API-issued download URLs, subtitles decode, and reversible start-from roundtrips for owned video fixtures
- read-only user config, OAuth grants, and tunnel routes decode
- history listing decode against the real API
- transfer list/count/info decode and typed pagination errors
- Files cursor continuation plus typed invalid-cursor errors for Files, Search, Trash, and Transfers

## Safety Rules

Allowed in `liveTest`:

- read-only probes
- reversible settings mutations with cleanup
- disposable file and trash flows with cleanup

Excluded from `liveTest`:

- destructive account mutations
- `use_start_from` mutations; disabling it permanently clears every saved per-file resume position
- IFTTT event mutations unless a dedicated non-production event target is available
- history clearing
- trash emptying
- any mutation without cleanup
