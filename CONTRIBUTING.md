# Contributing

## Setup

Install a Java `21` runtime and Node `^20.19.0` or `>=22.12.0`. `verify` runs
oxfmt through `npx` at the version pinned in [package.json](./package.json).

## Checks

```bash
./gradlew verify   # unit tests, ktlint, jar, line coverage floor, Markdown format
./gradlew test     # unit suite only
./gradlew liveTest # opt-in, real put.io API; excluded from verify
```

- `./gradlew spotlessApply` fixes ktlint findings.
- `npx --yes oxfmt@<version> '**/*.md'` fixes Markdown findings.
- The coverage floor and task graph live in [build.gradle.kts](./build.gradle.kts).
- [CI](./.github/workflows/ci.yml) also checks relative Markdown links and anchors, which `verify` does not.

## Live environment

The live suite reads these variables, from the environment first, then
`.env.local` and `.env`:

- `PUTIO_TOKEN_FIRST_PARTY` (falls back to `PUTIO_ACCESS_TOKEN`, then `PUTIO_TOKEN`)
- `PUTIO_CLIENT_ID`
- `PUTIO_PLAYBACK_FIXTURE_ID`: an owned, converted video in the dedicated profile
- `PUTIO_BASE_URL`: optional, defaults to `https://api.put.io/v2/`

Copy [.env.example](./.env.example) to use your own credentials. Maintainers
point `PUTIO_SDK_KOTLIN_SOPS_FILE` at the SOPS ciphertext and run
`make secrets-setup`, which needs SOPS 3.10+ and `jq` and writes an owner-only
`.env.local`. Run `make secrets-clean` before removing a worktree.

## Live safety rules

The live account is shared and real. Live tests in
[src/liveTest](./src/liveTest/kotlin/io/putdotio/sdk/live) may only:

- run read-only probes
- mutate settings reversibly, with cleanup
- create disposable files and trash entries, with cleanup

They must never:

- run destructive account mutations
- mutate `use_start_from`; disabling it clears every saved resume position
- send IFTTT events without a dedicated non-production target
- clear history or empty trash
- mutate anything without cleanup

## Pull requests

Follow the [pull request template](./.github/pull_request_template.md). The
commit type decides whether a merge publishes; see
[Distribution](./docs/distribution.md).
