# Security

If you believe you have found a security or privacy issue in this project,
please report it privately.

## Contact

- email: devs@put.io

If you are unsure whether something is sensitive, email first instead of opening
a public issue.

## Scope

Useful reports usually include issues involving:

- access token, secret, or credential exposure in code, tests, or fixtures
- unsafe handling of live-test secrets or `.env.local` material
- request or response handling that leaks account data
- unsafe workflow, release, or publishing guidance

## Guidelines

- test only against accounts, environments, and data you control
- keep testing non-destructive, low-volume, and service-safe
- use private email for suspected vulnerabilities

## Credential-bearing URLs

- API requests use the `Authorization` header where the endpoint supports it
- media URL builders include `oauth_token` because those endpoints accept URL authentication; treat returned URLs as credentials and do not log, persist, or share them
- SDK-created exceptions replace sensitive query values with `REDACTED`; request methods, paths, query names, and non-sensitive query values remain available for diagnosis
- redaction covers token, secret, password, credential, signature, API-key, authorization-code, session, and nonce parameter names, including common compound forms

## Supported Versions

This repository does not publish versioned releases yet. Report issues against
the current `main` branch unless maintainers say otherwise.

## Disclosure

Please allow a reasonable amount of time to investigate and fix the issue
before sharing details publicly.
