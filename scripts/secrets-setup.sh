#!/usr/bin/env bash

set -euo pipefail
umask 077

fail() {
  printf 'FAILED: %s\n' "$1" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

ciphertext="${PUTIO_SDK_KOTLIN_SOPS_FILE:?Set PUTIO_SDK_KOTLIN_SOPS_FILE to the SDK ciphertext file}"
output=".env.local"

command -v sops >/dev/null 2>&1 || fail "sops is required"
command -v jq >/dev/null 2>&1 || fail "jq is required"
command -v git >/dev/null 2>&1 || fail "git is required"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || fail "secrets setup must run inside the SDK git worktree"

[ -f "$ciphertext" ] || fail "ciphertext input must be one regular file"
[ ! -L "$ciphertext" ] || fail "ciphertext input must not be a symlink"
git check-ignore -q -- "$output" || fail "output path is not gitignored: $output"
[ ! -L "$output" ] || fail "output path must not be a symlink: $output"
[ ! -e "$output" ] || [ -f "$output" ] || fail "output path must be a regular file: $output"

status="$(sops filestatus --input-type dotenv "$ciphertext" 2>/dev/null)" \
  || fail "SOPS 3.10 or newer could not inspect the dotenv ciphertext input"
printf '%s\n' "$status" | grep -Eq '"encrypted"[[:space:]]*:[[:space:]]*true' \
  || fail "ciphertext input is not encrypted"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/putio-sdk-kotlin-secrets.XXXXXX")"
cleanup() {
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

payload_json="$tmp_dir/payload.json"
rendered_env="$tmp_dir/rendered.env"
sops decrypt --input-type dotenv --output-type json --output "$payload_json" "$ciphertext" \
  || fail "could not decrypt ciphertext input"
chmod 600 "$payload_json"

jq -r -f ./scripts/secrets-render.jq "$payload_json" > "$rendered_env" \
  || fail "decrypted payload failed validation or safe dotenv rendering"
chmod 600 "$rendered_env"
install -m 600 "$rendered_env" "$output"
printf 'ok wrote %s\n' "$output"
