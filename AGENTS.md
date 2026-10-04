# Agent Guide

## Repo

- Standalone Kotlin SDK repo for the put.io API, published as `io.put:putio-sdk-kotlin`
- Public package focused on Android-friendly Kotlin consumers; the first-party consumer is [putio-android](https://github.com/putdotio/putio-android), which pins the Maven Central release
- Namespaces and operations: [Architecture](./docs/ARCHITECTURE.md#current-namespace-scope)

## Start Here

- [Overview](./README.md)
- [Architecture](./docs/ARCHITECTURE.md)
- [Testing](./docs/TESTING.md)
- [Readiness](./docs/READINESS.md)
- [Distribution](./docs/DISTRIBUTION.md)

## Commands

- `./gradlew verify`: canonical local and CI guardrail; task graph in [build.gradle.kts](./build.gradle.kts)
- `./gradlew test`: unit suite only
- `./gradlew liveTest`: opt-in live suite; needs credentials from [Testing](./docs/TESTING.md#live-environment)
- `./gradlew spotlessApply`: fix formatting findings
- `./gradlew markdownCheck`: oxfmt Markdown check, part of `verify`; needs Node. `npx --yes oxfmt@0.70.0 '**/*.md'` fixes findings
- `./gradlew publishToMavenLocal -Pversion=X.Y.Z`: local dry run; see [Distribution](./docs/DISTRIBUTION.md#local-dry-run)
- `make secrets-setup` / `make secrets-clean`: write or remove the ignored live-test `.env.local` from `PUTIO_SDK_KOTLIN_SOPS_FILE` ([Makefile](./Makefile))

## Worktrees

`.worktreeinclude` carries `.env` and `.env.local` into Codex and Claude worktrees.
Run `make secrets-setup` if the live-test env is missing or stale.

## Repo-Specific Guidance

- Follow the [design rules](./docs/ARCHITECTURE.md#design-rules): mirror the domain-first API shape of `putio-sdk-typescript`, coroutine-first suspend functions, typed SDK errors, parsing at the boundary
- Keep the namespace surface small until a real app use case proves expansion
- Update docs when the public surface, verification flow, or publishing story changes
- Keep `README.md` consumer-facing and use `docs/*` for repo-operator detail

## Proof

- Docs only: `./gradlew markdownCheck` checks formatting, not links; confirm the commands and links you name resolve. No runtime proof.
- Source change: `./gradlew verify`, which enforces the 90% line coverage floor in [build.gradle.kts](./build.gradle.kts).
- API behavior `MockWebServer` cannot prove: `./gradlew liveTest`, inside the [safety rules](./docs/TESTING.md#safety-rules). The live account is shared and real; disabling `use_start_from`, for one, wipes every saved resume position.
- Public surface putio-android uses: build that app against this checkout by setting `putioSdkKotlinPath` in its `local.properties`.

## Delivery

Pull requests squash-merge to `main`. A push to `main` runs `verify`; when the commits since the last `v*` tag include `feat`, `fix`, `perf`, a revert, or a breaking change, semantic-release pushes the tag, publishes to Maven Central, and creates the GitHub release. `docs`, `chore`, `ci`, `test`, and `refactor` publish nothing. Central rejects a re-upload, so a failed publish is finished with the recovery dispatch in [Distribution](./docs/DISTRIBUTION.md#recovery); never delete or re-push its tag.
