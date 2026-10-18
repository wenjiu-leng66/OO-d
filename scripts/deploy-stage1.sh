#!/usr/bin/env sh
# Run from any directory: sh scripts/deploy-stage1.sh
set -eu
RBAC_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
RBAC_ROOT=$(CDPATH= cd -- "$RBAC_SCRIPT_DIR/.." && pwd)
RBAC_ENV_FILE=${RBAC_ENV_FILE:-"$RBAC_ROOT/deploy/.env"}

if ! command -v docker >/dev/null 2>&1; then
  echo 'Docker is required on the Linux server.' >&2
  exit 1
fi
docker compose version >/dev/null
if [ ! -f "$RBAC_ENV_FILE" ]; then
  echo 'Copy deploy/.env.example to deploy/.env and fill all passwords first.' >&2
  exit 1
fi
if grep -Eq 'REPLACE_|CHANGEME' "$RBAC_ENV_FILE"; then
  echo 'deploy/.env still contains placeholder passwords. Replace them locally.' >&2
  exit 1
fi
chmod 600 "$RBAC_ENV_FILE"
docker compose --env-file "$RBAC_ENV_FILE" -f "$RBAC_ROOT/deploy/compose.yaml" config --quiet
docker compose --env-file "$RBAC_ENV_FILE" -f "$RBAC_ROOT/deploy/compose.yaml" up --detach --build --wait --wait-timeout 300
docker compose --env-file "$RBAC_ENV_FILE" -f "$RBAC_ROOT/deploy/compose.yaml" ps
echo 'Ready. Open http://SERVER_IP:HTTP_PORT/stage1/ and run the documented HTTP self-test.'
