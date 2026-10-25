# Distribution

`io.put:putio-sdk-kotlin` on Maven Central, published through the Sonatype
Central Portal. The Maven group is put.io's reverse DNS; the Kotlin package
stays `io.putdotio.sdk`.

## Releases

Every push to `main` runs the `release` job in
[ci.yml](../.github/workflows/ci.yml) after `verify`, so every merge must be
releasable. [semantic-release](../.releaserc.json) reads the Conventional
Commits since the last `v*` tag:

- `fix:`, `perf:`, and reverts cut a patch, `feat:` a minor, and `!` or a
  `BREAKING CHANGE:` footer a major.
- `docs:`, `chore:`, `ci:`, `test:`, and `refactor:` publish nothing.
- A squash-merged revert cuts a patch even when the reverted commit is
  unreleased; the appended ` (#N)` stops the pair from matching. A
  `git revert` pushed straight to `main` cancels an unreleased original.

A release pushes the `v<version>` tag as the `putio-ci` App, publishes and
waits for Central validation, then creates the GitHub release. Central lists
the version within about half an hour.

Remote `publish*` tasks refuse the local `0.1.0-SNAPSHOT` version, so a build
without `-Pversion` cannot publish.

## Recovery

The tag is pushed before publishing, so a failed publish or GitHub release
leaves a tag without its artifact or release. Never push a new tag for it, and
never delete it: semantic-release would then cut the same version again, and
Central rejects a re-upload. Finish it from `main` instead:

```bash
gh workflow run ci.yml --repo putdotio/putio-sdk-kotlin --ref main -f recover_version=X.Y.Z
```

Recovery publishes only a version the Central Portal reports unpublished and
creates only a missing GitHub release. If the Portal still shows the
deployment as publishing, wait; a re-upload fails as a duplicate.

## Trust boundary

The release job runs `main`'s build scripts with the publishing credentials,
so the controls sit outside the workflow:

- the `release` environment is approval-free and its deployment rule allows
  only `main`, so no other ref can read its secrets
- the "Protect v* release tags" ruleset lets only organization admins and the
  `putio-ci` GitHub App create, move, or delete `v*` tags

## Credentials

Secrets live in the `release` environment; `verify` reads none. The put.io
1Password item
`frontend/putio-android-maven-sonatype` holds the Central token and the
signing key; `put.io/github-putio-ci-app` holds the App key.

| Name                                               | Kind     | Source                                                                             |
| -------------------------------------------------- | -------- | ---------------------------------------------------------------------------------- |
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | secret   | Central Portal user token for an account that owns the verified `io.put` namespace |
| `SIGNING_KEY_ID`                                   | secret   | Last eight hex characters of the GPG key id                                        |
| `SIGNING_KEY`                                      | secret   | ASCII-armored private key: `gpg --armor --export-secret-keys <id>`                 |
| `SIGNING_PASSWORD`                                 | secret   | The key's passphrase                                                               |
| `PUTIO_CI_APP_PRIVATE_KEY`                         | secret   | `putio-ci` App private key                                                         |
| `PUTIO_CI_APP_CLIENT_ID`                           | variable | `putio-ci` App client id                                                           |

The public key must be on `keyserver.ubuntu.com` or Central rejects the
signature:

```bash
gpg --full-generate-key                # RSA 4096, devs@put.io, 2y expiry
gpg --keyserver keyserver.ubuntu.com --send-keys <id>
```

## Local dry run

```bash
./gradlew publishToMavenLocal -Pversion=1.0.0
ls ~/.m2/repository/io/put/putio-sdk-kotlin/1.0.0/
```

Signing is skipped locally when no signing key is configured.
