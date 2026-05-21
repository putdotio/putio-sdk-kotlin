.PHONY: secrets-setup secrets-clean

SECRETS_OUTPUT ?= .env.local

secrets-setup:
	@infisical export --domain https://eu.infisical.com/api --projectId b2fcfbd7-19e0-4b87-a797-93d125c432ce --env dev --path /sdk-kotlin --format dotenv --output-file $(SECRETS_OUTPUT)
	@chmod 600 $(SECRETS_OUTPUT)

secrets-clean:
	rm -f .env.local .env.local.* .env.local.swp
