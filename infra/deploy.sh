#!/usr/bin/env bash
set -Eeuo pipefail
readonly APP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
fail() {
  printf 'Erro de deploy: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null || fail 'Docker não está instalado ou não está no PATH.'

cd "$APP_DIR"

for image in controle-gastos-backend:latest controle-gastos-web:latest; do
  docker image inspect "$image" >/dev/null 2>&1 || fail "Imagem local ausente: $image"
done

docker compose -f compose.yaml config --quiet
docker compose -f compose.yaml up --detach --no-build --pull never --remove-orphans
docker compose -f compose.yaml ps
