# Release

## Current State

This repo is bootstrapped for local verification and local Maven publishing:

```bash
./gradlew publishToMavenLocal
```

Automated public release publishing is not wired yet. The next release-focused step should decide:

1. public artifact coordinates
2. signing and registry credentials
3. whether release automation should mirror the semantic-release flow used by the Swift SDK or use a Gradle-native publish path

Until those decisions are made, keep `main` verify-first and avoid documenting an external package coordinate that does not exist yet.

