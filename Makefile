.PHONY: secrets-setup secrets-clean

secrets-setup:
	OP_ACCOUNT=putdotio.1password.com op whoami >/dev/null
	OP_ACCOUNT=putdotio.1password.com op inject -f -i .env.example -o .env.local

secrets-clean:
	rm -f .env.local .env.local.* .env.local.swp
