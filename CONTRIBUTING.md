# Contributing

## Setup

Install a Java `21` runtime and Node `^20.19.0` or `>=22.12.0` (for `oxfmt`).

## Checks

```bash
./gradlew verify            # local and CI gate
./gradlew test              # unit suite only
./gradlew liveTest          # opt-in, real put.io API; excludes error-path probes
./gradlew liveErrorPathTest # opt-in live tests that intentionally trigger 4xx responses
```

- `./gradlew spotlessApply` fixes ktlint findings.
- `npx --yes oxfmt@<version> '**/*.md'` fixes Markdown findings, with the version pinned in [package.json](./package.json).
- `./gradlew apiDump` updates [api/putio-sdk-kotlin.api](./api/putio-sdk-kotlin.api) after intentional public API changes.
- The coverage floor and task graph live in [build.gradle.kts](./build.gradle.kts).
- [CI](./.github/workflows/ci.yml) also checks relative Markdown links and anchors, which `verify` does not.

## Live environment

The live suite reads the variables in [.env.example](./.env.example) from the
environment first, then `.env.local` and `.env`; aliases and the optional
`PUTIO_BASE_URL` are in
[LiveSupport.kt](./src/liveTest/kotlin/io/putdotio/sdk/live/LiveSupport.kt).
`PUTIO_PLAYBACK_FIXTURE_ID` must be an owned, converted video with sidecar
subtitles on that account.

Pass credentials through the environment or copy `.env.example` to the
gitignored `.env.local` before running `./gradlew liveTest`.

## Live safety rules

Live tests hit a real put.io account. Tests in
[src/liveTest](./src/liveTest/kotlin/io/putdotio/sdk/live) may only:

- run read-only probes
- mutate settings reversibly, with cleanup
- create disposable files and trash entries, with cleanup
- add disposable transfers, app config keys, and device-linked OAuth tokens,
  then remove exactly the ones they created

They must never:

- run destructive account mutations
- mutate `use_start_from`; disabling it clears every saved resume position
- send IFTTT events without a dedicated non-production target
- clear history or empty trash
- mutate anything without cleanup

## Releases

- The squash commit's type decides whether a merge to `main` publishes ([.releaserc.json](./.releaserc.json)), from the `release` job in [ci.yml](./.github/workflows/ci.yml). Every merge must be releasable.
- A squash-merged `Revert "…"` commit publishes a patch release even when it reverts a `docs:` commit, and Maven Central never removes a published release.
- semantic-release pushes the tag before publishing. Never delete or re-push a `v*` tag: it would cut the same version again, and Central rejects a re-upload.
- If a publish or GitHub release fails, finish it from `main`. If the Central Portal still shows the deployment as publishing, wait first.

  ```bash
  gh workflow run ci.yml --repo putdotio/putio-sdk-kotlin --ref main -f recover_version=X.Y.Z
  ```

- Keep the `release` environment's deployment rule limited to `main`, and the "Protect v\* release tags" ruleset limited to org admins and the release GitHub App. The release job runs `main`'s build scripts with publishing credentials.
- `SIGNING_KEY_ID` is the PGP key ID's last eight hex characters, and `SIGNING_KEY` is `gpg --armor --export-secret-keys <id>`.
- When rotating the signing key, publish the public key to `keyserver.ubuntu.com`, or Central rejects the signature.
