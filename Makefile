.PHONY: secrets-setup secrets-clean

SECRETS_OUTPUT ?= .env.local

secrets-setup:
	SECRETS_OUTPUT="$(SECRETS_OUTPUT)" ./scripts/secrets-setup.sh

secrets-clean:
	rm -f .env.local .env.local.* .env.local.swp
