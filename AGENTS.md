# Agent Guide

## Repo

- Standalone Kotlin SDK repo for the put.io API
- Public package bootstrap focused on Android-friendly Kotlin consumers
- Namespaces and operations: [Architecture](./docs/ARCHITECTURE.md#current-namespace-scope)

## Start Here

- [Overview](./README.md)
- [Architecture](./docs/ARCHITECTURE.md)
- [Testing](./docs/TESTING.md)
- [Readiness](./docs/READINESS.md)
- [Release](./docs/RELEASE.md)

## Commands

- `./gradlew verify` — canonical local and CI guardrail; task graph in [build.gradle.kts](./build.gradle.kts)
- `./gradlew test` — unit suite only
- `./gradlew liveTest` — opt-in live suite; needs credentials from [Testing](./docs/TESTING.md#live-environment)
- `./gradlew spotlessApply` — fix formatting findings
- `./gradlew publishToMavenLocal -Pversion=X.Y.Z` — local dry run; see [Release](./docs/RELEASE.md#local-dry-run)
- `make secrets-setup` / `make secrets-clean` — write or remove the ignored live-test `.env.local` from `PUTIO_SDK_KOTLIN_SOPS_FILE` ([Makefile](./Makefile))

## Worktrees

`.worktreeinclude` carries `.env` and `.env.local` into Codex and Claude worktrees.
Run `make secrets-setup` if the live-test env is missing or stale.

## Repo-Specific Guidance

- Follow the [design rules](./docs/ARCHITECTURE.md#design-rules): mirror the domain-first API shape of `putio-sdk-typescript`, coroutine-first suspend functions, typed SDK errors, parsing at the boundary
- Keep the namespace surface small until a real app use case proves expansion
- Update docs when the public surface, verification flow, or publishing story changes
- Keep `README.md` consumer-facing and use `docs/*` for repo-operator detail
