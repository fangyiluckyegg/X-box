#!/usr/bin/env bash
#
# setup-host-ollama.sh — X-box 宿主 Ollama 一键初始化（Mac / Linux）
#
# 用途：让「比对」向量化功能可用的宿主侧前置步骤。完成：
#   1) 安装 Ollama（优先 brew；无 brew 直链下载 zip 并提取二进制，规避 HTTP/2 分帧错误）—— 已装则跳过
#   2) 启动 Ollama 并绑定 0.0.0.0:11434（容器经 host.docker.internal 可达）
#   3) 拉取向量模型 bge-m3:latest（已在本地则跳过；约 1.2GB，带 900s 超时）
#   4) 健康检查 + 真实监听地址校验（非 0.0.0.0 时给出手动修复提示）
#
# 背景：该逻辑原内联于 scripts/deploy.sh 的 setup_ollama()（阶段 3，见 deploy.sh:307）。
#       此处拆为可独立运行的脚本，便于不跑全量部署时单独准备 Ollama。
#       docker-compose.base.yml 的 AI_SERVICE_URL 指向 http://host.docker.internal:11434，
#       即本脚本启动的宿主 Ollama。
#
# 用法：
#   bash scripts/setup-host-ollama.sh                 # 完整安装+启动+拉取（幂等，可重复跑）
#   bash scripts/setup-host-ollama.sh --pull-only     # 仅拉模型（假定 ollama 已装已起）
#   bash scripts/setup-host-ollama.sh --skip-install  # 跳过安装（仅启动+拉取；假定 ollama 命令可用）
#   bash scripts/setup-host-ollama.sh --proxy http://127.0.0.1:7890   # 拉模型走代理
#   bash scripts/setup-host-ollama.sh -h              # 帮助
#
# 环境变量覆盖：
#   OLLAMA_HOST       监听地址（默认 0.0.0.0:11434）
#   OLLAMA_EMBED_MODEL 向量模型名（默认 bge-m3:latest，须与后端 AI_EMBED_MODEL 一致）
#
# 安全红线：不修改 docker-compose / 应用源码 / env 文件；仅在本机安装并启动 Ollama。
#           安装/拉取失败不阻断（返回非 0），由调用方决定是否降级。
#
# 注意：不使用 set -u（nounset）。macOS 自带 bash 3.2 的 set -u 在
# 「变量已赋值、但在函数 $* / "$@" 调用上下文的双引号实参里展开」时会误报
# unbound variable（实测 OLLAMA_HOST_VALUE / MODEL_NAME 均被误判，尽管已正确赋值）。
# 仅保留 pipefail，避免这类误报导致的非预期中断。
set -o pipefail

# ---------- 日志 ----------
log()  { printf '\033[32m[%s] [ollama] %s\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
warn() { printf '\033[33m[%s] [ollama] %s\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
err()  { printf '\033[31m[%s] [ollama] %s\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; }

# ---------- 可调参数 ----------
export OLLAMA_HOST_VALUE="${OLLAMA_HOST:-0.0.0.0:11434}"
MODEL_NAME="${OLLAMA_EMBED_MODEL:-bge-m3:latest}"
PULL_ONLY=0
SKIP_INSTALL=0
OLLAMA_PROXY=""
USED_BREW=0
# 拉取超时（秒）。默认 0 = 不限制（慢速网络下 1.2GB 模型可能需要数小时）；
# 可用环境变量 OLLAMA_PULL_TIMEOUT 覆盖，例如 OLLAMA_PULL_TIMEOUT=7200 限 2h。
PULL_TIMEOUT="${OLLAMA_PULL_TIMEOUT:-0}"
PLIST="$HOME/Library/LaunchAgents/homebrew.mxcl.ollama.plist"

# 确保常用安装位置在 PATH（无 brew 时 ollama 可能装到 ~/.local/bin，默认不一定在 PATH），
# 否则后续 command -v ollama 检测不到，会重复下载 139MB 安装包。
case ":$PATH:" in
  *":$HOME/.local/bin:"*) ;;
  *) export PATH="$HOME/.local/bin:$PATH" ;;
esac

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pull-only) PULL_ONLY=1 ;;
    --skip-install) SKIP_INSTALL=1 ;;
    --proxy) OLLAMA_PROXY="${2:-}"; shift ;;
    -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) err "未知参数: $1"; exit 2 ;;
  esac
  shift
done

if [[ -n "$OLLAMA_PROXY" ]]; then
  export HTTPS_PROXY="$OLLAMA_PROXY" HTTP_PROXY="$OLLAMA_PROXY"
  log "Proxy enabled: $OLLAMA_PROXY"
fi

# ---------- 0. 检测已就绪的 Ollama + 模型（复用快路径）----------
is_ollama_ready() {
  command -v ollama >/dev/null 2>&1 || return 1
  curl -sf "http://localhost:11434/api/tags" >/tmp/ollama_tags.json 2>/dev/null || return 1
  grep -q "$MODEL_NAME" /tmp/ollama_tags.json 2>/dev/null && return 0
  ollama list 2>/dev/null | grep -q "$MODEL_NAME" && return 0
  return 1
}

# ---------- 直链安装（无 brew 时；强制 HTTP/1.1 规避 macOS SecureTransport 的 HTTP/2 分帧错误）----------
install_ollama_direct() {
  # 固定安装到用户目录（~/.local/bin），避免 /usr/local 权限/结构差异。
  # ollama 运行时会基于自身可执行文件目录查找 lib/ollama/llama-server 等组件，
  # 因此必须保留 bin/ + lib/ 的目录结构，不能只复制 ollama 单一文件。
  local target_dir="$HOME/.local/bin"
  mkdir -p "$target_dir"
  local pkg_url="https://github.com/ollama/ollama/releases/latest/download/ollama-darwin.tgz"
  local tmp_pkg="/tmp/ollama-darwin.tgz"
  local tmp_dir="/tmp/ollama-install"
  local bin target_dir

  rm -rf "$tmp_dir" "$tmp_pkg"
  mkdir -p "$tmp_dir"

  # 单连接限速环境下，HTTP/1.1 + 多连接分片可叠加带宽（实测 8x）。
  # 优先用 python3 并行分片下载（天然规避 curl 的 HTTP/2 分帧错误）；无 python3 则回退 curl 单连接。
  local ok=0
  if command -v python3 >/dev/null 2>&1; then
    log "并行分片下载 Ollama 二进制包（HTTP/1.1 + 8 连接；单连接限速环境可提速数倍）..."
    if python3 - "$pkg_url" "$tmp_pkg" <<'PY'
import sys, urllib.request, concurrent.futures, os, time, threading
url, out = sys.argv[1], sys.argv[2]
def head():
    with urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=30) as r:
        return int(r.headers.get("Content-Length"))
try:
    total = head()
except Exception:
    total = 0
conns = 8
def part_size(fn):
    try:
        return os.path.getsize(fn)
    except OSError:
        return 0
def dl(a, b, fn):
    h = "bytes=%d-" % a if b == "" else "bytes=%d-%d" % (a, b)
    req = urllib.request.Request(url, headers={"Range": h})
    # 流式分片写入，便于进度监控实时累加各分片大小
    with urllib.request.urlopen(req, timeout=300) as r, open(fn, "wb") as f:
        while True:
            c = r.read(65536)
            if not c:
                break
            f.write(c)
if total and total > 0:
    chunk = (total + conns - 1) // conns
    ranges = [(i*chunk, min(i*chunk+chunk-1, total-1)) for i in range(conns)]
    parts = ["%s.part%d" % (out, i) for i in range(conns)]
else:
    ranges = [(0, "")]
    parts = ["%s.part0" % out]
# ---------- 进度监控：每 0.5s 汇总各分片大小，打印百分比 + 速度 ----------
stop = False
prev = 0.0
t_prev = time.time()
def fmt(n):
    if n >= 1<<30: return "%.2f GB" % (n/(1<<30))
    if n >= 1<<20: return "%.2f MB" % (n/(1<<20))
    if n >= 1<<10: return "%.2f KB" % (n/(1<<10))
    return "%d B" % n
def monitor():
    global prev, t_prev
    while not stop:
        cur = sum(part_size(p) for p in parts)
        now = time.time()
        dt = now - t_prev
        if dt >= 0.5:
            spd = (cur - prev) / dt if dt > 0 else 0
            if total and total > 0:
                pct = cur * 100.0 / total
                bl = 28
                filled = int(bl * cur / total)
                bar = "#" * filled + "-" * (bl - filled)
                sys.stdout.write("\r  [%s] %5.1f%%  %s / %s  %s/s" % (bar, pct, fmt(cur), fmt(total), fmt(spd)))
            else:
                sys.stdout.write("\r  %s  %s/s" % (fmt(cur), fmt(spd)))
            sys.stdout.flush()
            prev, t_prev = cur, now
        time.sleep(0.25)
mt = threading.Thread(target=monitor, daemon=True)
mt.start()
with concurrent.futures.ThreadPoolExecutor(max_workers=conns) as ex:
    for fut in [ex.submit(dl, a, b, fn) for (a, b), fn in zip(ranges, parts)]:
        fut.result()
stop = True
mt.join()
sys.stdout.write("\n")
with open(out, "wb") as fo:
    for p in parts:
        if os.path.exists(p):
            with open(p, "rb") as fi:
                fo.write(fi.read())
            os.remove(p)
if total and os.path.getsize(out) != total:
    raise SystemExit("size mismatch %d != %d" % (os.path.getsize(out), total))
print("downloaded", os.path.getsize(out), "bytes")
PY
    then
      ok=1
    else
      warn "python3 并行下载失败，回退 curl 单连接下载 ..."
    fi
  fi
  if [[ "$ok" -ne 1 ]]; then
    log "用 curl 单连接下载（强制 HTTP/1.1 + 断点续传，规避 HTTP/2 分帧错误）..."
    for attempt in 1 2 3 4 5; do
      log "下载尝试 $attempt/5 ..."
      if curl --http1.1 -fL -C - --retry 3 --retry-delay 5 -# -o "$tmp_pkg" "$pkg_url"; then
        ok=1; break
      fi
      sleep 3
    done
  fi
  [[ "$ok" -eq 1 ]] || { err "直链下载 Ollama 二进制包失败（可能网络不稳定）。"; return 1; }

  log "解包并提取 ollama 二进制与运行时库 ..."
  # ollama 运行时会基于自身可执行文件目录查找 lib/ollama/llama-server 等组件，
  # 因此必须保留 bin/ + lib/ 的目录结构，不能只复制 ollama 单一文件。
  tar xzf "$tmp_pkg" -C "$target_dir/.." || { err "解包 ollama-darwin.tgz 到 $target_dir/.. 失败。"; return 1; }
  [[ -x "$target_dir/ollama" ]] || { err "安装包中未找到 ollama 可执行文件（结构可能已变更）。"; return 1; }
  [[ -d "$target_dir/../lib/ollama" ]] || warn "安装包中未找到 lib/ollama 目录，向量化可能仍无法启动"

  command -v ollama >/dev/null 2>&1 || { err "ollama 安装后仍未在 PATH 中找到。"; return 1; }
  log "ollama 已安装：$(command -v ollama)（$(ollama --version 2>/dev/null | head -1)）"
}

if [[ "$PULL_ONLY" -eq 0 ]] && is_ollama_ready; then
  log "[成功] Ollama + $MODEL_NAME 已就绪，复用，跳过安装与拉取。"
  log "后端可通过 http://host.docker.internal:11434/api/embed 调用。"
  exit 0
fi
[[ "$PULL_ONLY" -eq 0 ]] && log "Ollama 未就绪或 $MODEL_NAME 缺失，开始自动安装 / 启动 / 拉取 ..."

# ---------- 1. 安装 Ollama（Mac / Linux）----------
if [[ "$PULL_ONLY" -eq 0 && "$SKIP_INSTALL" -eq 0 ]]; then
  if command -v ollama >/dev/null 2>&1; then
    log "检测到 ollama 已安装：$(command -v ollama)"
  elif command -v brew >/dev/null 2>&1; then
    log "开始用 brew 安装 ollama（进度实时显示，请稍候）..."
    brew install ollama || { err "brew install ollama 失败，请检查网络或 Homebrew。"; exit 1; }
  else
    log "未找到 Homebrew，改用直链下载安装 Ollama（规避官方脚本的 HTTP/2 下载分帧错误）..."
    install_ollama_direct || {
      warn "直链安装失败，回退到官方脚本（可能需要多次重试）..."
      curl -fsSL https://ollama.com/install.sh | sh || { err "官方脚本安装 Ollama 也失败。"; exit 1; }
      export PATH="$HOME/.local/bin:/usr/local/bin:$PATH"
    }
  fi
fi

# ---------- 2. 探测 launchd plist 是否已含 OLLAMA_HOST（用于第 6 步注入判断）----------
plist_has_host() { [[ -f "$PLIST" ]] && grep -q "OLLAMA_HOST" "$PLIST"; }
if [[ -f "$PLIST" ]]; then
  if plist_has_host; then
    log "launchd plist 已含 OLLAMA_HOST，将依其生效（不再重复注入）。"
  else
    log "launchd plist 未含 OLLAMA_HOST；若第 6 步校验未绑 0.0.0.0，将尝试用 plutil 注入。"
  fi
else
  log "未找到 brew ollama 的 launchd plist，将以环境变量方式启动（见第 3 步）。"
fi

# ---------- 3. 启动 Ollama ----------
start_ollama() {
  if pgrep -f "ollama serve" >/dev/null 2>&1 || pgrep -f "ollama app" >/dev/null 2>&1; then
    log "Ollama 进程已在运行。"
    return 0
  fi
  if command -v brew >/dev/null 2>&1 && brew services list 2>/dev/null | grep -q "^ollama "; then
    USED_BREW=1
    log "通过 brew services 启动 ollama ..."
    brew services start ollama || { warn "brew services start ollama 失败"; return 1; }
  else
    log "以前台方式启动 ollama serve（OLLAMA_HOST=${OLLAMA_HOST_VALUE:-0.0.0.0:11434}）..."
    OLLAMA_HOST="${OLLAMA_HOST_VALUE:-0.0.0.0:11434}" nohup ollama serve >/tmp/ollama-serve.log 2>&1 &
    disown || true
  fi
}
[[ "$PULL_ONLY" -eq 0 ]] && start_ollama || true

# ---------- 4. 拉取模型（已存在则跳过；超时可用 OLLAMA_PULL_TIMEOUT 调大，慢速网络勿设太小）----------
if ollama list 2>/dev/null | grep -q "$MODEL_NAME"; then
  log "模型 $MODEL_NAME 已在本地，跳过 pull（约 1.2GB 已就绪）。"
else
  if [[ "$PULL_TIMEOUT" -eq 0 ]]; then
    log "开始拉取 $MODEL_NAME（约 1.2GB 模型权重；不限制超时，慢速网络请耐心等待，可 Ctrl-C 中断）..."
    pull_cmd=(ollama pull "$MODEL_NAME")
  else
    log "开始拉取 $MODEL_NAME（约 1.2GB；超时 ${PULL_TIMEOUT}s，可用 OLLAMA_PULL_TIMEOUT 调大）..."
    pull_cmd=(timeout "$PULL_TIMEOUT" ollama pull "$MODEL_NAME")
  fi
  if ! "${pull_cmd[@]}"; then
    err "模型 $MODEL_NAME 拉取失败或超时（${PULL_TIMEOUT}s）。慢速网络请设 OLLAMA_PULL_TIMEOUT=0 重跑本步，或用 --proxy 加速。"
    exit 1
  fi
  log "拉取完成：$MODEL_NAME 已就绪。"
fi

# ---------- 5. 健康检查 ----------
log "等待 Ollama 就绪 ..."
for _ in $(seq 1 30); do
  curl -sf "http://localhost:11434/api/tags" >/dev/null 2>&1 && break
  sleep 2
done
if ! curl -sf "http://localhost:11434/api/tags" | grep -q "$MODEL_NAME"; then
  err "Ollama 未就绪或模型 $MODEL_NAME 未拉取成功。"
  exit 1
fi

# ---------- 6. 真实绑定校验 + 自愈 ----------
get_listener_hosts() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:11434 -sTCP:LISTEN 2>/dev/null | awk 'NR>1 {n=$9; sub(/:[0-9]+$/,"",n); gsub(/[\[\]]/,"",n); print n}'
  elif command -v ss >/dev/null 2>&1; then
    ss -tlnp 2>/dev/null | grep ':11434 ' | awk '{a=$4; sub(/:[0-9]+$/,"",a); gsub(/[\[\]]/,"",a); print a}'
  fi
}
verify_bind() {
  local hosts addr_str
  hosts="$(get_listener_hosts || true)"
  addr_str="${hosts//$'\n'/, }"; addr_str="${addr_str%, }"; [[ -z "$addr_str" ]] && addr_str="none"
  if printf '%s\n' "$hosts" | grep -qxF "0.0.0.0" || printf '%s\n' "$hosts" | grep -qxF "*"; then
    log "Host Ollama listening on 0.0.0.0:11434（容器经 host.docker.internal:11434 可达）"
    return 0
  fi
  warn "could not bind 0.0.0.0:11434; current listener(s): $addr_str; containers may NOT reach Ollama"
  warn "手动修复：在 $PLIST 的 <EnvironmentVariables> 增加 <key>OLLAMA_HOST</key><string>0.0.0.0:11434</string>"
  warn "          或直接：export OLLAMA_HOST=0.0.0.0:11434; ollama serve"
  return 1
}

verify_ok=0
verify_bind && verify_ok=1

# brew 路径启动且未绑 0.0.0.0，且 plist 存在、缺 OLLAMA_HOST、plutil 可用 → 注入并 restart
if [[ "$USED_BREW" -eq 1 && "$verify_ok" -ne 1 ]]; then
  if [[ -f "$PLIST" ]] && ! plist_has_host && command -v plutil >/dev/null 2>&1; then
    log "尝试用 plutil 向 $PLIST 的 <EnvironmentVariables> 注入 OLLAMA_HOST=0.0.0.0:11434 ..."
    if plutil -insert ":EnvironmentVariables:OLLAMA_HOST" -string "0.0.0.0:11434" "$PLIST" 2>/dev/null; then
      log "plist 注入成功，restart brew services ollama ..."
      brew services restart ollama || warn "brew services restart ollama 失败"
      sleep 3
      verify_bind && verify_ok=1 || true
    else
      warn "plutil 注入失败（plist 可能无 EnvironmentVariables 节点或格式异常），跳过自动注入。"
    fi
  fi
fi

# 通用重启兜底（非 brew 路径、或 brew plist 注入未生效）
if [[ "$verify_ok" -ne 1 ]]; then
  log "尝试自愈：通用重启 ollama 以重新绑定 OLLAMA_HOST=${OLLAMA_HOST_VALUE:-0.0.0.0:11434} ..."
  pkill -f "ollama serve" 2>/dev/null
  pkill -f ollama 2>/dev/null
  sleep 2
  if command -v brew >/dev/null 2>&1 && brew services list 2>/dev/null | grep -q "^ollama "; then
    log "通过 brew services restart ollama ..."
    brew services restart ollama || warn "brew services restart ollama 失败"
  else
    log "以前台方式重启 ollama serve（OLLAMA_HOST=${OLLAMA_HOST_VALUE:-0.0.0.0:11434}）..."
    OLLAMA_HOST="0.0.0.0:11434" nohup ollama serve >/tmp/ollama-serve.log 2>&1 &
    disown || true
  fi
  sleep 3
  verify_bind || true
fi

# ---------- 7. 统一成功日志 ----------
log "[成功] Ollama + $MODEL_NAME 就绪"
log "后端可通过 http://host.docker.internal:11434/api/embed 调用；无需重启后端（每次请求现连）。"
