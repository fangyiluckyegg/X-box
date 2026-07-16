#!/usr/bin/env bash
#
# setup-host-ollama.sh — Mac 宿主原生部署 Ollama + bge-m3（X-box）
#
# 用途：在开发机 / 生产机（macOS）上安装并初始化 Ollama，使其以
#       OLLAMA_HOST=0.0.0.0:11434 监听，并拉取 bge-m3 模型。
#       后端容器（prj-backend-c）通过 host.docker.internal:11434 访问本机 Ollama。
#
# 前置：macOS + Homebrew（或已装 Ollama）；Docker Desktop / OrbStack 已安装。
# 用法：bash scripts/setup-host-ollama.sh             # 安装 + 启动 + 拉模型 + 校验
#       bash scripts/setup-host-ollama.sh --pull-only # 仅拉取 / 校验模型
#
# 说明：
#   - OLLAMA_HOST 必须 0.0.0.0（非 127.0.0.1），否则容器经网关 IP 连不上。
#   - host.docker.internal 由 Docker Desktop / OrbStack 内置解析，无需 extra_hosts。

set -euo pipefail

OLLAMA_HOST_VALUE="0.0.0.0:11434"
MODEL_NAME="bge-m3:latest"
PULL_ONLY=0

for arg in "$@"; do
  case "$arg" in
    --pull-only) PULL_ONLY=1 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

log() { echo "[setup-host-ollama] $*"; }

# ---------- 1. 安装 Ollama（Mac / Homebrew）----------
if [[ "$PULL_ONLY" -eq 0 ]]; then
  if command -v ollama >/dev/null 2>&1; then
    log "检测到 ollama 已安装：$(command -v ollama)"
  else
    if ! command -v brew >/dev/null 2>&1; then
      log "未找到 Homebrew，请先安装：https://brew.sh" >&2
      exit 1
    fi
    log "通过 brew 安装 ollama ..."
    brew install ollama
  fi
fi

# ---------- 2. 持久化 OLLAMA_HOST = 0.0.0.0:11434 ----------
# 优先引导用户用 launchd 环境变量方式（适用于 brew 安装的 ollama 作为服务）。
# 若用 `ollama serve` 前台启动，则本脚本在第 3 步导出该环境变量。
persist_ollama_host() {
  local plist="$HOME/Library/LaunchAgents/homebrew.mxcl.ollama.plist"
  if [[ -f "$plist" ]]; then
    if grep -q "OLLAMA_HOST" "$plist"; then
      log "launchd plist 已含 OLLAMA_HOST，跳过写入。"
    else
      log "请在 $plist 的 <dict> 内 <EnvironmentVariables> 增加："
      log '  <key>OLLAMA_HOST</key><string>0.0.0.0:11434</string>'
      log "（或手动执行：launchctl setenv OLLAMA_HOST 0.0.0.0:11434 后重启 ollama 服务）"
    fi
  else
    log "未找到 brew ollama 的 launchd plist，将以环境变量方式启动（见第 3 步）。"
  fi
}
persist_ollama_host

# ---------- 3. 启动 Ollama ----------
start_ollama() {
  if pgrep -f "ollama serve" >/dev/null 2>&1 || pgrep -f "ollama app" >/dev/null 2>&1; then
    log "Ollama 进程已在运行。"
    return 0
  fi
  if command -v brew >/dev/null 2>&1 && brew services list 2>/dev/null | grep -q "^ollama "; then
    log "通过 brew services 启动 ollama ..."
    brew services start ollama
  else
    log "以前台方式启动 ollama serve（OLLAMA_HOST=$OLLAMA_HOST_VALUE）..."
    export OLLAMA_HOST="$OLLAMA_HOST_VALUE"
    nohup ollama serve >/tmp/ollama-serve.log 2>&1 &
    disown || true
  fi
}

if [[ "$PULL_ONLY" -eq 0 ]]; then
  start_ollama
fi

# ---------- 4. 拉取模型 bge-m3 ----------
log "拉取模型 $MODEL_NAME（首次需下载权重，可能较慢）..."
ollama pull "$MODEL_NAME"

# ---------- 5. 健康检查 ----------
log "等待 Ollama 就绪 ..."
for _ in $(seq 1 30); do
  if curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -sf "http://localhost:11434/api/tags" | grep -q "$MODEL_NAME"; then
  log "错误：Ollama 未就绪或模型 $MODEL_NAME 未拉取成功。" >&2
  exit 1
fi

log "✅ 宿主 Ollama 就绪：$MODEL_NAME 已加载，监听 $OLLAMA_HOST_VALUE"
log "后端可通过 http://host.docker.internal:11434/api/embed 调用。"
