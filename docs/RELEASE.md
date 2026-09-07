# Release

## Current State

This repo is bootstrapped for local verification and local Maven publishing:

```bash
./gradlew publishToMavenLocal
```

Maven Central publishing is tracked in [#43](https://github.com/putdotio/putio-sdk-kotlin/issues/43):
coordinates `io.putdotio:putio-sdk-kotlin`, GPG signing from an Actions secret,
and a tag-driven release lane gated on `./gradlew verify`.

Until the first release ships, keep `main` verify-first and do not document an
external package coordinate that does not resolve yet.

