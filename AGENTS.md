# Agent Guide

## Repo

- Kotlin SDK for the put.io API, published as `io.put:putio-sdk-kotlin`.
- First-party consumer: [putio-android](https://github.com/putdotio/putio-android), which pins the Maven Central release.
- `README.md` is for consumers, [CONTRIBUTING.md](./CONTRIBUTING.md) for contributors and maintainers.

## Commands

- `./gradlew verify` is the local and CI gate; [CONTRIBUTING.md](./CONTRIBUTING.md#checks) lists the other check and fix commands.
- `./gradlew markdownCheck`: oxfmt Markdown check, part of `verify`; needs Node.
- `./gradlew publishToMavenLocal -Pversion=X.Y.Z`: local publish dry run
- `make secrets-setup` writes the ignored live-test `.env.local` when it is missing or stale ([setup](./CONTRIBUTING.md#live-environment)); run `make secrets-clean` before removing a worktree.

## Rules

- Keep the public surface close to [putio-sdk-typescript](https://github.com/putdotio/putio-sdk-typescript).
- Keep Android UI and app lifecycle types out of the SDK.
- `files.copy` wraps `/files/copy-to-disk`, kept for an older client; point new callers at `sharing.cloneSharedFiles`.

## Proof

- Docs only: `./gradlew markdownCheck`. It does not check links; confirm the commands and links you name resolve.
- Source change: `./gradlew verify`.
- API behavior `MockWebServer` cannot prove: `./gradlew liveTest`, inside the [live safety rules](./CONTRIBUTING.md#live-safety-rules).
- Public surface putio-android uses: build that app against this checkout by setting `putioSdkKotlinPath` in its `local.properties`.

## Delivery

Pull requests squash-merge to `main`. The squash commit's type decides whether a merge publishes to Maven Central ([.releaserc.json](./.releaserc.json)). A squash-merged `Revert "…"` commit publishes a patch even when it reverts a `docs:` commit, and Central never takes a release back. Never delete or re-push a `v*` tag; finish a failed publish through [Releases](./CONTRIBUTING.md#releases).
