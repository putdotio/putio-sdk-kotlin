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
- `./gradlew verify` is the canonical guardrail and currently covers compile, test, jar, and unit-coverage verification
- GitHub Actions runs the default verify lane on `blacksmith-2vcpu-ubuntu-2404`
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
- `PUTIO_1PASSWORD_RUNTIME_ITEM_ID`
- `PUTIO_1PASSWORD_RUNTIME_VAULT`

The live harness prefers direct env vars first, then falls back to a shared 1Password runtime item when `OP_SERVICE_ACCOUNT_TOKEN`, `PUTIO_1PASSWORD_RUNTIME_ITEM_ID`, and `PUTIO_1PASSWORD_RUNTIME_VAULT` are all set.

The shared runtime item name stays out of git. Provide it through your local env or ignored operator config before running `./gradlew liveTest`.

## Live Scope

The first live layer follows the TypeScript SDK convention of separating safe runtime verification from the default unit suite.

Current live targets cover:

- token validation and OOB auth-code fetch
- account info and reversible account settings mutation
- disposable file create, search, trash restore, and cleanup flows
- history listing decode against the real API

## Safety Rules

Allowed in `liveTest`:

- read-only probes
- reversible settings mutations with cleanup
- disposable file and trash flows with cleanup

Excluded from `liveTest`:

- destructive account mutations
- history clearing
- trash emptying
- any mutation without cleanup
