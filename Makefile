.PHONY: secrets-setup secrets-clean

secrets-setup:
	./scripts/secrets-setup.sh

secrets-clean:
	rm -f .env.local .env.local.* .env.local.swp
