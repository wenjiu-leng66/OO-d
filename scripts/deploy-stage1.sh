#!/usr/bin/env sh
# Run from any directory: sh scripts/deploy-stage1.sh
set -eu
# 使用脚本自身位置定位仓库，避免从其他工作目录运行时加载到错误的 compose/.env。
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
# 先拒绝未替换的模板值再启动，不把 Compose 展开的完整配置（包含密码）打印出来。
if grep -Eq 'REPLACE_|CHANGEME' "$RBAC_ENV_FILE"; then
  echo 'deploy/.env still contains placeholder passwords. Replace them locally.' >&2
  exit 1
fi
chmod 600 "$RBAC_ENV_FILE"
docker compose --env-file "$RBAC_ENV_FILE" -f "$RBAC_ROOT/deploy/compose.yaml" config --quiet
# --wait 以健康检查为启动完成条件；不删除命名卷，也不自动执行原始 Excel 的全量重建。
docker compose --env-file "$RBAC_ENV_FILE" -f "$RBAC_ROOT/deploy/compose.yaml" up --detach --build --wait --wait-timeout 300
docker compose --env-file "$RBAC_ENV_FILE" -f "$RBAC_ROOT/deploy/compose.yaml" ps
echo 'Ready. Open http://SERVER_IP:HTTP_PORT/stage1/ and run the documented HTTP self-test.'
