# Distribution

## Coordinates

`io.put:putio-sdk-kotlin` on Maven Central, published through the Sonatype
Central Portal. The Maven group is the reverse DNS of put.io; the Kotlin
package stays `io.putdotio.sdk`.

## How a release happens

Every push to `main` runs the `release` job in
[`ci.yml`](../.github/workflows/ci.yml) after verify passes, so every merge to
`main` should already be releasable. [semantic-release](../.releaserc.json)
reads the Conventional Commits since the last `v*` tag:

- `fix:`, `perf:`, and reverts cut a patch, `feat:` a minor, and `!` or a
  `BREAKING CHANGE:` footer a major
- a revert is a `Revert "<header>"` commit whose body keeps
  `This reverts commit <sha>.`, as `git revert` and GitHub's Revert button
  write it. Squash-merged, it cuts a patch even when the reverted commit is
  unreleased, and that commit still counts: the appended ` (#N)` keeps the
  pair from matching. A `git revert` pushed straight to `main` cancels an
  unreleased original, and neither counts
- `docs:`, `chore:`, `ci:`, `test:`, and `refactor:` cut nothing; the run ends
  without publishing

When a release is due, semantic-release pushes the `v<version>` tag as the
`putio-ci` App, runs
`./gradlew publishToMavenCentral -Pversion=<version>`, waits for Central
validation, and creates the GitHub release with generated notes. Central lists
the version within about half an hour. No version file is committed back.

Local builds keep the version `0.1.0-SNAPSHOT`. Every remote `publish*` task
refuses a SNAPSHOT version, so a build without `-Pversion` cannot publish even
with credentials present.

## Recovery

The tag is pushed before publishing, so a failed publish or GitHub release
leaves a tag without its artifact or release. Never push a new tag for it, and
never delete it: semantic-release would then cut the same version again, and
Central rejects a re-upload. Finish it from `main` instead:

```bash
gh workflow run ci.yml --repo putdotio/putio-sdk-kotlin --ref main -f recover_version=X.Y.Z
```

A secretless job first checks that the tag exists and is on `main`. The
recovery job then checks out that commit, publishes only when the Central
Portal reports the version unpublished, and creates the GitHub release only
when the API reports it missing. If the Portal still shows the deployment as
publishing, wait for it to finish before recovering; a re-upload fails as a
duplicate. A dispatch without `recover_version` skips recovery and runs only
`verify`, whose scan audits every workflow.

## Trust boundary

The release job runs `main`'s build scripts with the publishing credentials,
so the controls sit outside the workflow:

- the `release` environment is approval-free and its deployment rule allows
  only `main`, so no other ref can read its secrets
- the "Protect v* release tags" ruleset lets only organization admins and the
  `putio-ci` GitHub App create, move, or delete `v*` tags
- release jobs run without the shared Gradle cache

## Credentials

All secrets live in the `release` environment on the GitHub repository. None
are checked in or read by `./gradlew verify`. The put.io 1Password item
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
