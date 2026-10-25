# Agent Guide

## Repo

- Kotlin SDK for the put.io API, published as `io.put:putio-sdk-kotlin`.
- First-party consumer: [putio-android](https://github.com/putdotio/putio-android), which pins the Maven Central release.
- `README.md` is for consumers, [CONTRIBUTING.md](./CONTRIBUTING.md) for contributors and maintainers.

## Commands

- `./gradlew verify`: the local and CI gate; task graph in [build.gradle.kts](./build.gradle.kts)
- `./gradlew test`: unit suite only
- `./gradlew liveTest`: opt-in live suite; credentials in [CONTRIBUTING.md](./CONTRIBUTING.md#live-environment)
- `./gradlew spotlessApply`: fix ktlint findings
- `./gradlew markdownCheck`: oxfmt Markdown check, part of `verify`; needs Node. `npx --yes oxfmt@<version> '**/*.md'` fixes findings, with the version pinned in [package.json](./package.json)
- `./gradlew publishToMavenLocal -Pversion=X.Y.Z`: local publish dry run
- `make secrets-setup` / `make secrets-clean`: write or remove the ignored live-test `.env.local` from `PUTIO_SDK_KOTLIN_SOPS_FILE`. Run setup when it is missing or stale; clean before removing a worktree.

## Rules

- Keep the public surface close to [putio-sdk-typescript](https://github.com/putdotio/putio-sdk-typescript).
- Keep Android UI and app lifecycle types out of the SDK.
- `files.copy` wraps `/files/copy-to-disk`, kept for an older client; point new callers at `sharing.cloneSharedFiles`.
- Update README or CONTRIBUTING when the public surface, verification flow, or publishing changes.

## Proof

- Docs only: `./gradlew markdownCheck`. It does not check links; confirm the commands and links you name resolve.
- Source change: `./gradlew verify`.
- API behavior `MockWebServer` cannot prove: `./gradlew liveTest`, inside the [live safety rules](./CONTRIBUTING.md#live-safety-rules).
- Public surface putio-android uses: build that app against this checkout by setting `putioSdkKotlinPath` in its `local.properties`.

## Delivery

Pull requests squash-merge to `main`. The squash commit's type decides whether a merge publishes to Maven Central ([.releaserc.json](./.releaserc.json)). A squash-merged `Revert "…"` commit publishes a patch even when it reverts a `docs:` commit, and Central never takes a release back. Never delete or re-push a `v*` tag; finish a failed publish through [Releases](./CONTRIBUTING.md#releases).
