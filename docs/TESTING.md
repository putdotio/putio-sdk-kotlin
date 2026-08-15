# Testing

## Commands

```bash
./gradlew test
./gradlew verify
./gradlew liveTest
```

Install a Java `21` runtime with your preferred version manager or system package manager before running these commands. The checked-in `.java-version` is only a compatibility hint for tools that choose to honor it.

## Current Verification Shape

- `./gradlew test` runs the repository test suite
- `./gradlew verify` is the canonical guardrail and currently covers compile, test, jar, and a `90%` line coverage floor
- GitHub Actions runs the default verify lane on `ubuntu-latest`
- request and response behavior is exercised with `MockWebServer`
- the unit suite also exercises the localized user-facing error mapping layer
- `./gradlew liveTest` runs an opt-in live suite against the real put.io API and is excluded from the default `test` and `verify` tasks
- JaCoCo HTML and XML reports are written under `build/reports/jacoco/test`

## Live Environment

Default example env file:

- `.env.example`

Supported environment variables:

- `PUTIO_TOKEN_FIRST_PARTY`
- `PUTIO_ACCESS_TOKEN`
- `PUTIO_TOKEN`
- `PUTIO_CLIENT_ID`
- `PUTIO_BASE_URL`

Run `make secrets-setup` with `PUTIO_SDK_KOTLIN_SOPS_FILE` pointing to the
maintainer-supplied SOPS ciphertext. The command requires SOPS 3.10 or newer
and `jq`, rejects plaintext or malformed payloads, and writes owner-only
`.env.local`. The live harness auto-loads `.env.local` and `.env`;
already-exported environment variables keep highest priority. Run
`make secrets-clean` before removing the worktree.

## Live Scope

The first live layer follows the TypeScript SDK convention of separating safe runtime verification from the default unit suite.

Current live targets cover:

- token validation and OOB auth-code fetch
- account info and reversible account settings mutation
- disposable file create, search, trash restore, and cleanup flows
- playback-adjacent subtitles decode and reversible start-from roundtrips for owned video fixtures
- read-only user config, OAuth grants, and tunnel routes decode
- history listing decode against the real API
- transfer list/count/info decode and typed pagination errors

## Safety Rules

Allowed in `liveTest`:

- read-only probes
- reversible settings mutations with cleanup
- disposable file and trash flows with cleanup

Excluded from `liveTest`:

- destructive account mutations
- IFTTT event mutations unless a dedicated non-production event target is available
- history clearing
- trash emptying
- any mutation without cleanup
