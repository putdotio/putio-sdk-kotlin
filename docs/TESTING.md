# Testing

## Commands

```bash
./gradlew test
./gradlew verify
```

## Current Verification Shape

- `./gradlew test` runs the repository test suite
- `./gradlew verify` is the canonical guardrail and currently covers compile and test checks
- request and response behavior is exercised with `MockWebServer`

## Current Gap

This bootstrap does not include live tests against the real put.io API yet. When the namespace surface grows or auth behavior changes, add live verification before treating the package as publication-ready.

