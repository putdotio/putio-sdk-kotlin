# Agent Guide

## Repo

- Kotlin SDK for the put.io API, published as `io.put:putio-sdk-kotlin`.
- First-party consumer: [putio-android](https://github.com/putdotio/putio-android), which pins the Maven Central release.
- `README.md` is for consumers, [CONTRIBUTING.md](./CONTRIBUTING.md) for contributors, [docs/distribution.md](./docs/distribution.md) for releases.
- Open [Architecture](./docs/architecture.md) before changing the public surface, errors, device-code linking, or playback.

## Commands

- `./gradlew verify`: the local and CI gate; task graph in [build.gradle.kts](./build.gradle.kts)
- `./gradlew test`: unit suite only
- `./gradlew liveTest`: opt-in live suite; credentials in [CONTRIBUTING.md](./CONTRIBUTING.md#live-environment)
- `./gradlew spotlessApply`: fix ktlint findings
- `./gradlew markdownCheck`: oxfmt Markdown check, part of `verify`; needs Node. `npx --yes oxfmt@<version> '**/*.md'` fixes findings, with the version pinned in [package.json](./package.json)
- `./gradlew publishToMavenLocal -Pversion=X.Y.Z`: local publish dry run
- `make secrets-setup` / `make secrets-clean`: write or remove the ignored live-test `.env.local` from `PUTIO_SDK_KOTLIN_SOPS_FILE`

## Worktrees

`.worktreeinclude` carries `.env` and `.env.local` into Codex and Claude worktrees.
Run `make secrets-setup` if the live-test env is missing or stale.

## Rules

- Follow the [design rules](./docs/architecture.md#design-rules).
- Update docs when the public surface, verification flow, or publishing changes.

## Proof

- Docs only: `./gradlew markdownCheck`. It does not check links; confirm the commands and links you name resolve.
- Source change: `./gradlew verify`, which enforces the line coverage floor.
- API behavior `MockWebServer` cannot prove: `./gradlew liveTest`, inside the [live safety rules](./CONTRIBUTING.md#live-safety-rules). The live account is shared and real.
- Public surface putio-android uses: build that app against this checkout by setting `putioSdkKotlinPath` in its `local.properties`.

## Delivery

Pull requests squash-merge to `main`. The commit type decides whether a merge publishes to Maven Central; [Distribution](./docs/distribution.md) has the rules. Central rejects a re-upload, so finish a failed publish with the [recovery dispatch](./docs/distribution.md#recovery); never delete or re-push its tag.
