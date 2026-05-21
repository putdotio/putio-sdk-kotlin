.PHONY: secrets-setup secrets-clean

SECRETS_OUTPUT ?= .env.local
PUTIO_INFISICAL_DOMAIN ?= https://eu.infisical.com/api
PUTIO_SDK_KOTLIN_INFISICAL_ENV ?= dev

secrets-setup:
	@set -eu; \
	: "$${PUTIO_SDK_KOTLIN_INFISICAL_PROJECT_ID:?set PUTIO_SDK_KOTLIN_INFISICAL_PROJECT_ID for this repo}"; \
	: "$${PUTIO_SDK_KOTLIN_INFISICAL_PATH:?set PUTIO_SDK_KOTLIN_INFISICAL_PATH for this repo}"; \
	umask 077; \
	tmp="$$(mktemp)"; \
	trap 'rm -f "$$tmp"' EXIT; \
	infisical export --silent --domain "$(PUTIO_INFISICAL_DOMAIN)" --projectId "$$PUTIO_SDK_KOTLIN_INFISICAL_PROJECT_ID" --env "$(PUTIO_SDK_KOTLIN_INFISICAL_ENV)" --path "$$PUTIO_SDK_KOTLIN_INFISICAL_PATH" --format dotenv --output-file "$$tmp"; \
	install -m 600 "$$tmp" "$(SECRETS_OUTPUT)"

secrets-clean:
	rm -f .env.local .env.local.* .env.local.swp
