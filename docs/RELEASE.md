# Release

## Coordinates

`io.put:putio-sdk-kotlin` on Maven Central, published through the Sonatype
Central Portal by `.github/workflows/release.yml`. The Maven group is the
reverse DNS of put.io; the Kotlin package stays `io.putdotio.sdk`.

## Cut a release

1. Land the release commit on `main` with CI green.
2. Tag it and push the tag:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

3. The workflow verifies the tagged commit, publishes with the tag's version,
   waits for Central validation, and creates the GitHub release with generated
   notes. Central lists the version within about half an hour.

Local builds keep the version `0.1.0-SNAPSHOT`. A version is only ever set from
a tag through `-Pversion`; every remote `publish*` task refuses a SNAPSHOT version,
so `main` cannot publish by accident even with credentials present.

The tagged commit's build scripts run with the publishing credentials, so the
trust boundary sits outside the workflow, where a tag cannot rewrite it:

- The "Release tags" ruleset lets only repository admins create, move, or delete
  `v*` tags.
- The `release` environment requires a maintainer to approve each run before the
  publish job can read its secrets, and its deployment rule allows only `v*` tags
  (a branch rule would block every tag-triggered run).

Before approving, confirm the tag is on `main`:

```bash
git fetch origin main --tags
git merge-base --is-ancestor v0.1.0 origin/main && echo on-main
```

The workflow repeats that ancestry check and rejects tags that are not strict
`vMAJOR.MINOR.PATCH`, as a guard against mistakes rather than the control.

## Credentials

All secrets live in the `release` environment on the GitHub repository. None are
checked in or read by `./gradlew verify`.

| Secret | Source |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | Central Portal user token for an account that owns the verified `io.put` namespace |
| `SIGNING_KEY_ID` | Last eight hex characters of the GPG key id |
| `SIGNING_KEY` | ASCII-armored private key: `gpg --armor --export-secret-keys <id>` |
| `SIGNING_PASSWORD` | The key's passphrase |

The public key must be on `keyserver.ubuntu.com` or Central rejects the
signature:

```bash
gpg --full-generate-key                # RSA 4096, devs@put.io, 2y expiry
gpg --keyserver keyserver.ubuntu.com --send-keys <id>
```

## Local dry run

```bash
./gradlew publishToMavenLocal -Pversion=0.1.0
ls ~/.m2/repository/io/put/putio-sdk-kotlin/0.1.0/
```

Signing is skipped locally when no signing key is configured.
