#!/usr/bin/env bash
# 凭证契约校验：确保 dev / prod 后端连接共享 dev-mysql 的 prj_user 口令一致。
# 约束链：.env.dev 的 SPRING_DATASOURCE_PASSWORD 必须 == .env.prod.backend 的 SPRING_DATASOURCE_PASSWORD
#         （.env.prod 的 PRJ_DB_PWD 须为同值冗余项；dev-mysql 的 prj_user 口令由 wrapper 从
#          SPRING_DATASOURCE_PASSWORD 注入，prod 后端据此连接）
#
# 用法：bash scripts/verify-credential-contract.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_DEV="$ROOT/.env.dev"
ENV_PROD_BACKEND="$ROOT/.env.prod.backend"
ENV_PROD="$ROOT/.env.prod"

fail=0

val() { # $1=file $2=key -> 取最后一个 key= 的值
  local f="$1" k="$2"
  [ -f "$f" ] || { echo "❌ 缺失文件: $f"; return 1; }
  grep -E "^${k}=" "$f" | tail -n1 | cut -d= -f2-
}

DEV_PWD=$(val "$ENV_DEV" SPRING_DATASOURCE_PASSWORD || true)
PROD_PWD=$(val "$ENV_PROD_BACKEND" SPRING_DATASOURCE_PASSWORD || true)
PROD_PRJ=$(val "$ENV_PROD" PRJ_DB_PWD || true)

echo "== 凭证契约校验 =="
echo "  .env.dev           SPRING_DATASOURCE_PASSWORD = ${DEV_PWD:-<空>}"
echo "  .env.prod.backend  SPRING_DATASOURCE_PASSWORD = ${PROD_PWD:-<空>}"
echo "  .env.prod          PRJ_DB_PWD                 = ${PROD_PRJ:-<空>}"

if [ -z "${DEV_PWD}" ] || [ -z "${PROD_PWD}" ]; then
  echo "❌ dev 或 prod.backend 的 SPRING_DATASOURCE_PASSWORD 为空，prod 后端将连不上 dev-mysql！"
  fail=1
fi

if [ "${DEV_PWD}" != "${PROD_PWD}" ]; then
  echo "❌ .env.dev 与 .env.prod.backend 的 prj_user 口令不一致（prod 后端无法连接 dev-mysql）"
  fail=1
else
  echo "✅ dev / prod.backend prj_user 口令一致"
fi

if [ -n "${PROD_PRJ}" ] && [ "${PROD_PRJ}" != "${DEV_PWD}" ]; then
  echo "⚠️  .env.prod 的 PRJ_DB_PWD 与 dev 口令不一致（冗余项失配，需同步）"
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "契约校验未通过，请修正后重试。"
  exit 1
fi
echo "✅ 凭证契约校验通过"
