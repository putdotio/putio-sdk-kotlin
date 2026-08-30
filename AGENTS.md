# Agent Guide

## Repo

- Standalone Kotlin SDK repo for the put.io API
- Public package bootstrap focused on Android-friendly Kotlin consumers
- Current namespace scope: `account`, `auth`, `appConfig`, `files`, `grants`, `history`, `ifttt`, `routes`, `trash`, and `transfers`

## Start Here

- [Overview](./README.md)
- [Architecture](./docs/ARCHITECTURE.md)
- [Testing](./docs/TESTING.md)
- [Readiness](./docs/READINESS.md)
- [Release](./docs/RELEASE.md)

## Commands

- `./gradlew verify`
- `./gradlew test`
- `./gradlew liveTest`
- `./gradlew spotlessApply`
- `./gradlew publishToMavenLocal`
- `make secrets-setup`
- `make secrets-clean`

## Worktrees

`.worktreeinclude` carries `.env` files into Codex and Claude worktrees. Run
`./gradlew verify`; use `make secrets-setup` with
`PUTIO_SDK_KOTLIN_SOPS_FILE` if live-test env is missing or stale.

## Repo-Specific Guidance

- Keep the public API closer to `putio-sdk-typescript` than the legacy Swift callback surface
- Prefer coroutine-first suspend functions and typed SDK errors
- Live tests accept maintainer-supplied `PUTIO_SDK_KOTLIN_SOPS_FILE`; `make secrets-setup` validates and writes ignored `.env.local`, and `make secrets-clean` removes it.
- Parse external data at the boundary and keep models domain-first
- Keep the namespace surface intentionally small until a real app use case proves expansion
- Use `./gradlew verify` as the canonical local and CI guardrail
- Update docs when the public surface, verification flow, or publishing story changes
- Keep `README.md` consumer-facing and use `docs/*` for repo-operator detail
