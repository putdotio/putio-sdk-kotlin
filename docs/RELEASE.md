# Release

## Coordinates

`io.put:putio-sdk-kotlin` on Maven Central, published through the Sonatype
Central Portal. The Maven group is the reverse DNS of put.io; the Kotlin
package stays `io.putdotio.sdk`.

## How a release happens

Every push to `main` runs the `release` job in
[`ci.yml`](../.github/workflows/ci.yml) after verify passes, once a maintainer
approves the `release` environment. [semantic-release](../.releaserc.json)
reads the Conventional Commits since the last `v*` tag:

- `fix:` and `perf:` cut a patch, `feat:` a minor, and `!` or a
  `BREAKING CHANGE:` footer a major
- `docs:`, `chore:`, `ci:`, `test:`, and `refactor:` cut nothing; the run ends
  without publishing

When a release is due, semantic-release pushes the `v<version>` tag, runs
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

The recovery job checks out the tag, publishes only when the POM is not yet on
`repo1.maven.org`, and creates the GitHub release only when it is missing.

## Trust boundary

The release job runs `main`'s build scripts with the publishing credentials,
so the controls sit outside the workflow:

- the `release` environment requires a maintainer to approve each run before
  any job can read its secrets, and its deployment rule allows only `main`
- the "Protect v* release tags" ruleset lets only organization admins and the
  `putio-releaser` GitHub App create, move, or delete `v*` tags
- release jobs run without the shared Gradle cache

## Credentials

All secrets live in the `release` environment on the GitHub repository. None
are checked in or read by `./gradlew verify`. The put.io 1Password item
`frontend/putio-android-maven-sonatype` holds the Central token and the
signing key; `put.io/github-putio-releaser-app` holds the App key.

| Name | Kind | Source |
| --- | --- | --- |
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | secret | Central Portal user token for an account that owns the verified `io.put` namespace |
| `SIGNING_KEY_ID` | secret | Last eight hex characters of the GPG key id |
| `SIGNING_KEY` | secret | ASCII-armored private key: `gpg --armor --export-secret-keys <id>` |
| `SIGNING_PASSWORD` | secret | The key's passphrase |
| `PUTIO_RELEASE_BOT_PRIVATE_KEY` | secret | `putio-releaser` App private key |
| `PUTIO_RELEASE_BOT_CLIENT_ID` | variable | `putio-releaser` App client id |

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
