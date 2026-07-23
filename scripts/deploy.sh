#!/usr/bin/env bash
#
# deploy.sh — X-box 统一部署脚本（Mac / Linux），支持通过 --env 指定目标环境
#
# 用途：一条命令完成 X-box 任意环境（dev/prod/staging）部署：
#   阶段 0  解析参数 + 前置检查（Docker 守护进程 / docker compose）
#   阶段 1  按所选环境准备 env 文件（缺失则从 .example 复制；ChangeMe_* 占位符自动生成强随机值；仅处理该环境需要的文件，绝不触碰其它环境真实值）
#   阶段 2  校验凭证契约（不同环境不同规则）
#   阶段 3  准备宿主 Ollama（除非 --skip-ollama）
#   阶段 4  按所选环境启动栈（docker compose ... up -d --build）
#   阶段 5  等待关键服务健康并探测入口
#   阶段 6  --dry-run 时仅执行阶段 0/1/2
#
# 用法：
#   bash scripts/deploy.sh --env prod                 # 生产（默认）
#   bash scripts/deploy.sh --env dev                  # 开发
#   bash scripts/deploy.sh --env staging              # 预发（需先提供 docker-compose.staging.yml 与 .env.staging.example）
#   bash scripts/deploy.sh --env prod --skip-ollama   # 跳过 Ollama 准备
#   bash scripts/deploy.sh --env prod --dry-run       # 仅检查/准备 env 并校验契约，不启动
#   bash scripts/deploy.sh --env prod --proxy http://127.0.0.1:7890
#
# 安全红线：
#   - 绝不硬编码真实密码；仅生成随机值或保留用户已有值
#   - 绝不覆盖用户手动设置的非占位符值（仅替换 ChangeMe_*）
#   - 不在日志中打印生成的密码明文
#   - env 文件以 UTF-8 无 BOM + LF 写入（awk/printf，不引入 CRLF）
#   - 不修改 docker-compose.*.yml / 网关配置 / 应用源码
#
# 正确且安全的部署机流程（与 deploy.ps1 头部说明保持一致）：
#   保持 .env.prod 被 gitignore，部署机自己持有真实文件：
#     cd <部署机 X-box 目录>
#   1) 确保 .env.prod / .env.prod.backend 里是安全密码
#      —— 直接把开发机那两文件里的明文串拷过来填进去（可抄我们生成的几串），
#         或者 cp .env.prod.example .env.prod 后手动填。总之：别进 git。
#   2) 清卷（当前卷里还是旧的坏密码，必须清）
#      docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod down -v
#   3) 部署
#      bash scripts/deploy.sh --env prod
#
set -euo pipefail

# 任何未预期命令失败 → 打印致命错误并中止
trap 'echo "[$(date "+%Y-%m-%d %H:%M:%S")] [deploy] 致命错误：第 $LINENO 行命令失败，部署已中止。" >&2; exit 1' ERR

# ---------- 切换到项目根目录（脚本位于 scripts/）----------
cd "$(dirname "$0")/.."

# ---------- 参数解析 ----------
ENV="prod"
SKIP_OLLAMA=0
DRY_RUN=0
PROXY=""
OLLAMA_STATUS="unknown"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENV="${2:-}"; shift ;;
    --skip-ollama) SKIP_OLLAMA=1 ;;
    --dry-run) DRY_RUN=1 ;;
    --proxy) PROXY="${2:-}"; shift ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
  shift
done
case "$ENV" in
  dev|prod|staging) ;;
  *) echo "未知环境: $ENV（可选 dev|prod|staging）" >&2; exit 2 ;;
esac

START_TS=$(date +%s)

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [deploy] $*"; }

# ---------- 工具函数 ----------
# 生成 32 位 [A-Za-z0-9] 强随机串
gen_rand() {
  openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c 32
}

# 读取 env 文件中某 key 的当前值（忽略注释/空行；value 可含特殊字符）
get_env_val() {
  local file="$1" key="$2"
  [[ -f "$file" ]] || return 0
  awk -F= -v k="$key" '{ key2=$1; gsub(/^[ \t]+/,"",key2); if (key2==k) {print substr($0, index($0,"=")+1); exit} }' "$file"
}

# 若 key 当前值为 ChangeMe 占位符则写入新值；否则保留（不覆盖真实值）
# 若 key 不存在则追加一行
set_env_val_if_placeholder() {
  local file="$1" key="$2" newval="$3"
  local cur
  cur="$(get_env_val "$file" "$key")"
  if [[ -n "$cur" && "$cur" != *"ChangeMe"* ]]; then
    return 0
  fi
  if grep -q "^[[:space:]]*${key}=" "$file"; then
    awk -v k="$key" -v v="$newval" '{ if ($0 ~ "^[[:space:]]*" k "=") {print k"="v} else {print} }' "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
  else
    printf '%s\n' "${key}=${newval}" >> "$file"
  fi
}

# 从若干 "file:key" 中取第一个真实（非 ChangeMe）值；都没有则返回新随机值
derive_master() {
  local pair val file key
  for pair in "$@"; do
    file="${pair%%:*}"; key="${pair#*:}"
    val="$(get_env_val "$file" "$key")"
    if [[ -n "$val" && "$val" != *ChangeMe* ]]; then
      echo "$val"
      return 0
    fi
  done
  gen_rand
}

# ---------- 环境 → 配置映射 ----------
COMPOSE_FILES=()
ENV_FILE=""
ENV_FILES=()
ENV_EXAMPLES=()
WAIT_SERVICES=()
CRITICAL_SERVICES=()

resolve_env() {
  case "$ENV" in
    dev)
      COMPOSE_FILES=("docker-compose.base.yml" "docker-compose.business-prj.dev.yml")
      ENV_FILE=".env.dev"
      ENV_FILES=(.env.dev .env.backend)
      ENV_EXAMPLES=(.env.dev.example .env.backend.example)
      WAIT_SERVICES=(mysql redis prj-backend-c prj-frontend prj-php)
      CRITICAL_SERVICES=(mysql redis prj-backend-c)
      ;;
    prod)
      COMPOSE_FILES=("docker-compose.base.yml" "docker-compose.prod.yml")
      ENV_FILE=".env.prod"
      ENV_FILES=(.env.dev .env.prod .env.prod.backend)
      ENV_EXAMPLES=(.env.dev.example .env.prod.example .env.prod.backend.example)
      WAIT_SERVICES=(mysql redis prj-redis prj-backend-c prj-frontend prj-php)
      CRITICAL_SERVICES=(mysql redis prj-redis prj-backend-c)
      ;;
    staging)
      COMPOSE_FILES=("docker-compose.base.yml" "docker-compose.staging.yml")
      ENV_FILE=".env.staging"
      ENV_FILES=(.env.staging .env.staging.backend)
      ENV_EXAMPLES=(.env.staging.example .env.staging.backend.example)
      WAIT_SERVICES=(mysql redis prj-backend-c prj-frontend prj-php)
      CRITICAL_SERVICES=(mysql redis prj-backend-c)
      ;;
  esac
}

# 以当前环境的 compose 文件与 env-file 构造 docker compose 命令
compose_run() {
  local args=()
  local f
  for f in "${COMPOSE_FILES[@]}"; do args+=( -f "$f" ); done
  args+=( --env-file "$ENV_FILE" )
  docker compose "${args[@]}" "$@"
}

# 阶段 0：前置检查
check_prereqs() {
  log "===== 阶段 0：前置检查 ====="
  if ! docker info >/dev/null 2>&1; then
    log "错误：Docker 守护进程未运行，请先启动 Docker。" >&2
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1; then
    log "错误：docker compose 不可用（需要 Docker Compose V2）。" >&2
    exit 1
  fi
  log "Docker 守护进程与 docker compose 就绪。"
}

# 阶段 1：按所选环境检查并准备 env 文件（仅处理本环境 map 中的文件）
prepare_env_files() {
  log "===== 阶段 1：检查并准备 env 文件（环境：$ENV）====="
  local i
  for i in "${!ENV_FILES[@]}"; do
    local f="${ENV_FILES[$i]}" ex="${ENV_EXAMPLES[$i]}"
    if [[ ! -f "$f" ]]; then
      if [[ ! -f "$ex" ]]; then
        log "错误：模板 $ex 不存在，无法创建 $f" >&2
        exit 1
      fi
      cp "$ex" "$f"
      log "已基于 $ex 创建 $f"
    fi
  done

  # 仅基于本环境 map 中的文件推导/对齐主数据源口令（其它环境文件绝不触碰）
  local master=""
  case "$ENV" in
    dev)
      master="$(derive_master ".env.dev:SPRING_DATASOURCE_PASSWORD" ".env.dev:PRJ_DB_PWD" ".env.backend:SPRING_DATASOURCE_PASSWORD")"
      set_env_val_if_placeholder .env.dev SPRING_DATASOURCE_PASSWORD "$master"
      set_env_val_if_placeholder .env.dev PRJ_DB_PWD "$master"
      set_env_val_if_placeholder .env.backend SPRING_DATASOURCE_PASSWORD "$master"
      ;;
    prod)
      master="$(derive_master ".env.dev:SPRING_DATASOURCE_PASSWORD" ".env.prod.backend:SPRING_DATASOURCE_PASSWORD" ".env.prod:PRJ_DB_PWD" ".env.dev:PRJ_DB_PWD")"
      set_env_val_if_placeholder .env.dev SPRING_DATASOURCE_PASSWORD "$master"
      set_env_val_if_placeholder .env.dev PRJ_DB_PWD "$master"
      set_env_val_if_placeholder .env.prod.backend SPRING_DATASOURCE_PASSWORD "$master"
      set_env_val_if_placeholder .env.prod PRJ_DB_PWD "$master"
      ;;
    staging)
      master="$(derive_master ".env.staging:PRJ_DB_PWD" ".env.staging.backend:SPRING_DATASOURCE_PASSWORD" ".env.dev:SPRING_DATASOURCE_PASSWORD")"
      set_env_val_if_placeholder .env.staging PRJ_DB_PWD "$master"
      set_env_val_if_placeholder .env.staging.backend SPRING_DATASOURCE_PASSWORD "$master"
      ;;
  esac

  # 逐文件替换其余 ChangeMe 占位符（仅本环境 map 中的文件；UTF-8 无 BOM + LF）
  local f
  for f in "${ENV_FILES[@]}"; do
    local tmp="${f}.tmp"
    : > "$tmp"
    while IFS= read -r line || [[ -n "$line" ]]; do
      if [[ "$line" =~ ^([A-Za-z0-9_]+)=(.*)$ ]]; then
        local k="${BASH_REMATCH[1]}" v="${BASH_REMATCH[2]}"
        if [[ "$v" == *ChangeMe* ]]; then
          printf '%s=%s\n' "$k" "$(gen_rand)" >> "$tmp"
        else
          printf '%s\n' "$line" >> "$tmp"
        fi
      else
        printf '%s\n' "$line" >> "$tmp"
      fi
    done < "$f"
    mv "$tmp" "$f"
  done
  log "env 文件准备完成（仅处理环境 $ENV 所需文件；占位符已替换；已有真实值已保留）。"
}

# 阶段 1.5：SSL 证书自检（证书被 gitignore，换机器部署可能缺失；缺失则自动生成自签证书，幂等）
ensure_ssl_cert() {
  local crt="gateway/nginx/ssl/prj.crt"
  local key="gateway/nginx/ssl/prj.key"
  if [[ -f "$crt" && -f "$key" ]]; then
    log "SSL 证书已存在，跳过生成。"
    return 0
  fi
  log "SSL 证书缺失，尝试自动生成自签证书（prj.crt / prj.key）..." >&2
  mkdir -p "$(dirname "$crt")"
  if ! command -v openssl >/dev/null 2>&1; then
    log "错误：未找到 openssl，无法自动生成证书。请手动生成后重试：" >&2
    log "  cd gateway/nginx/ssl" >&2
    log "  openssl req -x509 -newkey rsa:2048 -nodes -keyout prj.key -out prj.crt -days 3650 -subj \"/CN=localhost\"" >&2
    exit 1
  fi
  openssl req -x509 -newkey rsa:2048 -nodes -keyout "$key" -out "$crt" -days 3650 -subj "/CN=localhost" 2>/dev/null || true
  if [[ -f "$crt" && -f "$key" ]]; then
    log "SSL 自签证书已生成：$(dirname "$crt")"
  else
    log "错误：证书生成失败，请按上方命令手动生成。" >&2
    exit 1
  fi
}

# 阶段 2：凭证契约校验（不同环境不同规则）
validate_contract() {
  log "===== 阶段 2：校验凭证契约（环境：$ENV）====="
  local ok=1 dev_ds be_ds prod_be_ds prod_db st_db st_be_ds
  case "$ENV" in
    dev)
      dev_ds="$(get_env_val .env.dev SPRING_DATASOURCE_PASSWORD)"
      be_ds="$(get_env_val .env.backend SPRING_DATASOURCE_PASSWORD)"
      if [[ -z "$dev_ds" || -z "$be_ds" ]]; then log "凭证契约校验失败：SPRING_DATASOURCE_PASSWORD 存在空值。" >&2; ok=0; fi
      if [[ "$dev_ds" != "$be_ds" ]]; then log "dev 凭证契约校验失败：.env.dev 与 .env.backend 的 SPRING_DATASOURCE_PASSWORD 不一致。" >&2; ok=0; fi
      ;;
    prod)
      dev_ds="$(get_env_val .env.dev SPRING_DATASOURCE_PASSWORD)"
      prod_be_ds="$(get_env_val .env.prod.backend SPRING_DATASOURCE_PASSWORD)"
      prod_db="$(get_env_val .env.prod PRJ_DB_PWD)"
      if [[ -z "$dev_ds" || -z "$prod_be_ds" || -z "$prod_db" ]]; then log "prod 凭证契约校验失败：SPRING_DATASOURCE_PASSWORD / PRJ_DB_PWD 存在空值。" >&2; ok=0; fi
      if [[ "$dev_ds" != "$prod_be_ds" ]]; then log "prod 凭证契约校验失败：.env.dev 的 SPRING_DATASOURCE_PASSWORD 与 .env.prod.backend 的不一致。" >&2; ok=0; fi
      if [[ "$prod_db" != "$prod_be_ds" ]]; then log "prod 凭证契约校验失败：.env.prod 的 PRJ_DB_PWD 与 .env.prod.backend 的 SPRING_DATASOURCE_PASSWORD 不一致。" >&2; ok=0; fi
      ;;
    staging)
      st_db="$(get_env_val .env.staging PRJ_DB_PWD)"
      st_be_ds="$(get_env_val .env.staging.backend SPRING_DATASOURCE_PASSWORD)"
      if [[ -z "$st_db" || -z "$st_be_ds" ]]; then log "staging 凭证契约校验失败：PRJ_DB_PWD / SPRING_DATASOURCE_PASSWORD 存在空值。" >&2; ok=0; fi
      if [[ "$st_db" != "$st_be_ds" ]]; then log "staging 凭证契约校验失败：.env.staging 的 PRJ_DB_PWD 与 .env.staging.backend 的 SPRING_DATASOURCE_PASSWORD 不一致。" >&2; ok=0; fi
      if [[ -f .env.dev ]]; then
        dev_ds="$(get_env_val .env.dev SPRING_DATASOURCE_PASSWORD)"
        if [[ -n "$dev_ds" && "$dev_ds" != *ChangeMe* && "$dev_ds" != "$st_be_ds" ]]; then
          log "提示：.env.dev 的 SPRING_DATASOURCE_PASSWORD 与 staging 不同，如需跨环境复用数据库请手动对齐（脚本不修改 .env.dev）。"
        fi
      fi
      ;;
  esac
  if [[ "$ok" -ne 1 ]]; then
    log "请手动修复（脚本不会自动覆盖你的密码）。" >&2
    exit 1
  fi
  log "凭证契约校验通过：$ENV 环境数据源口令一致。"
}

# 阶段 3：Ollama 准备（原 setup-host-ollama.sh 已内联为 setup_ollama）
# 安装/启动/绑定 0.0.0.0:11434/拉取 bge-m3/健康检查。
# 支持 --skip-install（跳过安装）与 --proxy <url>（透传到拉取）。
setup_ollama() {
  local OLLAMA_HOST_VALUE="0.0.0.0:11434"
  local MODEL_NAME="bge-m3:latest"
  local PULL_ONLY=0
  local SKIP_INSTALL=0
  local OLLAMA_PROXY=""
  local USED_BREW=0
  local PLIST="$HOME/Library/LaunchAgents/homebrew.mxcl.ollama.plist"

  # 解析内联参数（向后兼容 --pull-only；新增 --skip-install / --proxy）
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --pull-only) PULL_ONLY=1 ;;
      --skip-install) SKIP_INSTALL=1 ;;
      --proxy) OLLAMA_PROXY="${2:-}"; shift ;;
      *) log "未知参数: $1" >&2; return 2 ;;
    esac
    shift
  done

  # 代理透传（作用于 ollama pull 等网络操作）
  if [[ -n "$OLLAMA_PROXY" ]]; then
    export HTTPS_PROXY="$OLLAMA_PROXY"
    export HTTP_PROXY="$OLLAMA_PROXY"
    log "Proxy enabled: $OLLAMA_PROXY"
  fi

  # ---------- 1. 安装 Ollama（Mac / Homebrew）----------
  if [[ "$PULL_ONLY" -eq 0 && "$SKIP_INSTALL" -eq 0 ]]; then
    if command -v ollama >/dev/null 2>&1; then
      log "检测到 ollama 已安装：$(command -v ollama)"
    else
      if ! command -v brew >/dev/null 2>&1; then
        log "未找到 Homebrew，请先安装：https://brew.sh" >&2
        return 1
      fi
      log "通过 brew 安装 ollama ..."
      if ! brew install ollama; then return 1; fi
    fi
  fi

  # ---------- 2. 探测 launchd plist 是否已含 OLLAMA_HOST（用于第 6 步自动注入判断）----------
  # 不在此处写入：若 brew 路径启动且第 6 步校验发现未绑 0.0.0.0，才用 plutil 安全注入。
  plist_has_host() {
    [[ -f "$PLIST" ]] && grep -q "OLLAMA_HOST" "$PLIST"
  }
  if [[ -f "$PLIST" ]]; then
    if plist_has_host; then
      log "launchd plist 已含 OLLAMA_HOST，将依其生效（不再重复注入）。"
    else
      log "launchd plist 未含 OLLAMA_HOST；若第 6 步校验未绑 0.0.0.0，将尝试用 plutil 注入并 restart。"
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
      brew services start ollama || { log "brew services start ollama 失败" >&2; return 1; }
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

  # ---------- 4. 拉取模型 bge-m3（已存在则跳过，对齐 deploy.ps1 L614-629）----------
  log "检查模型 $MODEL_NAME 是否已在本地 ..."
  if ollama list 2>/dev/null | grep -q "$MODEL_NAME"; then
    log "模型 $MODEL_NAME 已在本地，跳过 pull。"
  else
    log "拉取模型 $MODEL_NAME（首次需下载权重，可能较慢）..."
    if ! ollama pull "$MODEL_NAME"; then
      log "错误：模型 $MODEL_NAME 拉取失败。" >&2
      return 1
    fi
  fi

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
    return 1
  fi

  # ---------- 6. 真实绑定校验（与 deploy.ps1 Get-OllamaListeners / 实际地址报告对齐）----------
  # 黄色 WARNING 输出，对齐 deploy.ps1 -ForegroundColor Yellow
  warn() {
    printf '\033[33m[%s] [deploy] %s\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
  }
  # 探测 11434 真实 TCP 监听地址：Mac 用 lsof，Linux 用 ss；解析出 Local Address 的 host 部分。
  get_listener_hosts() {
    if command -v lsof >/dev/null 2>&1; then
      # 例：ollama ... TCP *:11434 (LISTEN) 或 127.0.0.1:11434 / [::]:11434
      lsof -nP -iTCP:11434 -sTCP:LISTEN 2>/dev/null | awk 'NR>1 {n=$9; sub(/:[0-9]+$/,"",n); gsub(/[\[\]]/,"",n); print n}'
    elif command -v ss >/dev/null 2>&1; then
      # 例：LISTEN 0 4096 0.0.0.0:11434 0.0.0.0:* users:(...)
      ss -tlnp 2>/dev/null | grep ':11434 ' | awk '{a=$4; sub(/:[0-9]+$/,"",a); gsub(/[\[\]]/,"",a); print a}'
    fi
  }
  # 校验是否真实监听到 0.0.0.0（lsof 的 * 通配等价）；0=成功(打印 OK)，1=失败(打印 WARN)。
  verify_bind() {
    local hosts addr_str
    hosts="$(get_listener_hosts || true)"
    addr_str="${hosts//$'\n'/, }"
    addr_str="${addr_str%, }"
    [[ -z "$addr_str" ]] && addr_str="none"
    if printf '%s\n' "$hosts" | grep -qxF "0.0.0.0" || printf '%s\n' "$hosts" | grep -qxF "*"; then
      log "✅ [OK] Host Ollama ready: $MODEL_NAME loaded, listening on 0.0.0.0:11434"
      log "后端可通过 http://host.docker.internal:11434/api/embed 调用。"
      return 0
    fi
    # 未绑到 0.0.0.0（如 127.0.0.1 / [::] / none）→ 警告 + 诊断（语气对齐 deploy.ps1 L607-610、L652-661）
    warn "could not bind 0.0.0.0:11434; current listener(s): $addr_str; containers may NOT reach Ollama"
    warn "手动修复：在 $PLIST 的 <EnvironmentVariables> 增加 <key>OLLAMA_HOST</key><string>0.0.0.0:11434</string>"
    warn "          或直接：export OLLAMA_HOST=0.0.0.0:11434; ollama serve"
    return 1
  }

  # 先跑一次校验
  local verify_ok=0
  verify_bind && verify_ok=1

  # 若 brew 路径启动且未绑 0.0.0.0，且 plist 存在、缺 OLLAMA_HOST、plutil 可用：
  # 用 plutil 注入 EnvironmentVariables 的 OLLAMA_HOST，再 restart 并重跑校验（安全：已含则跳过，不重复注入）。
  if [[ "$USED_BREW" -eq 1 && "$verify_ok" -ne 1 ]]; then
    if [[ -f "$PLIST" ]] && ! plist_has_host && command -v plutil >/dev/null 2>&1; then
      log "尝试用 plutil 向 $PLIST 的 <EnvironmentVariables> 注入 OLLAMA_HOST=0.0.0.0:11434 ..."
      if plutil -insert ":EnvironmentVariables:OLLAMA_HOST" -string "0.0.0.0:11434" "$PLIST" 2>/dev/null; then
        log "plist 注入成功，restart brew services ollama ..."
        brew services restart ollama || warn "brew services restart ollama 失败"
        sleep 3
        verify_bind || true
      else
        warn "plutil 注入失败（plist 可能无 EnvironmentVariables 节点或格式异常），跳过自动注入。"
      fi
    fi
  fi
}

# 阶段 3：Ollama 准备（wrapper：处理 --skip-ollama 开关，--proxy 透传给内联函数）
prepare_ollama() {
  if [[ "$SKIP_OLLAMA" -eq 1 ]]; then
    log "===== 阶段 3：跳过 Ollama 准备（--skip-ollama）====="
    OLLAMA_STATUS="skipped"
    return 0
  fi
  log "===== 阶段 3：准备宿主 Ollama ..."
  local ollama_args=()
  if [[ -n "$PROXY" ]]; then ollama_args=(--proxy "$PROXY"); fi
  if setup_ollama "${ollama_args[@]}"; then
    OLLAMA_STATUS="ok"
    log "宿主 Ollama 准备完成。"
  else
    # Ollama 仅是「向量化 / 语义检索」类功能的软依赖：后端容器启动时不强依赖它，
    # embedding 仅在请求时懒调用，缺失时按请求抛错优雅降级。故准备失败不阻断部署。
    OLLAMA_STATUS="failed"
    log "警告：Ollama 准备失败，部署将继续；但「向量化 / 语义检索」类功能暂不可用。" >&2
    log "修复：手动安装 Ollama 后 'ollama serve --host 0.0.0.0:11434'；或重跑时加 --skip-ollama 跳过重复安装。" >&2
  fi
}

# 阶段 3.5：预建单文件 bind mount 日志空文件（dev/prod 对齐）
# 单文件 bind mount 要求宿主侧对应文件预先存在，否则 Docker 会建成同名目录导致挂载失败。
# 这些文件已被 .gitignore 屏蔽（logs/** / *.log），不进 git。
ensure_log_paths() {
  log "===== 阶段 3.5：预建单文件 bind mount 日志空文件（环境：$ENV）====="
  local files=(
    "logs/mysql/error.log"
    "logs/redis/redis.log"
    "logs/nginx/access.log"
    "logs/nginx/error.log"
  )
  case "$ENV" in
    dev)
      files+=("logs/prj-frontend/dev.log")
      ;;
    prod|staging)
      files+=("logs/prj-frontend/access.log" "logs/prj-frontend/error.log")
      ;;
  esac
  local f
  for f in "${files[@]}"; do
    mkdir -p "$(dirname "$f")"
    if [[ ! -f "$f" ]]; then
      : > "$f"
      log "已创建日志空文件：$f"
    fi
  done
  log "日志路径预建完成（dev/prod 对齐：均确保单文件挂载落盘点存在）。"
}

# 阶段 4：按所选环境启动栈
start_stack() {
  log "===== 阶段 4：启动 $ENV 栈 ====="
  if ! compose_run up -d --build; then
    log "错误：$ENV 栈启动失败，请查看上方日志（docker compose logs）。" >&2
    exit 1
  fi
  log "$ENV 栈已启动。"
}

# 等待某服务就绪（running 且 healthy；无 healthcheck 则仅看 running）。服务不存在则视为就绪跳过。
wait_service() {
  local svc="$1" i line state health
  if ! compose_run ps --format '{{.Service}}' 2>/dev/null | grep -qx "$svc"; then
    return 0
  fi
  for i in $(seq 1 60); do
    line="$(compose_run ps --format '{{.Service}}|{{.State}}|{{.Health}}' 2>/dev/null | grep "^${svc}|" || true)"
    state="$(echo "$line" | cut -d'|' -f2)"
    health="$(echo "$line" | cut -d'|' -f3)"
    if [[ "$state" == "running" ]]; then
      if [[ -z "$health" || "$health" == "healthy" ]]; then
        return 0
      fi
    fi
    sleep 2
  done
  return 1
}

# 探测某 URL（带重试）
probe_url() {
  local url="$1" i
  for i in $(seq 1 30); do
    if curl -sf "$url" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  return 1
}

# 判断某服务是否为关键服务（健康失败须中断）
is_critical() {
  local svc="$1" c
  for c in "${CRITICAL_SERVICES[@]}"; do
    [[ "$c" == "$svc" ]] && return 0
  done
  return 1
}

# 阶段 5：健康检查 + 探测 + 地址清单
health_and_probe() {
  log "===== 阶段 5：等待服务就绪并探测入口（环境：$ENV）====="
  local svc files_str
  files_str="-f ${COMPOSE_FILES[0]} -f ${COMPOSE_FILES[1]}"
  for svc in "${WAIT_SERVICES[@]}"; do
    if wait_service "$svc"; then
      log "$svc 就绪。"
    else
      if is_critical "$svc"; then
        log "错误：关键服务 $svc 在 120 秒内未达就绪状态，部署未成功。请排查：docker compose -f $files_str logs $svc" >&2
        exit 1
      else
        log "警告：$svc 在 120 秒内未达就绪状态，请排查：docker compose -f $files_str logs $svc"
      fi
    fi
  done

  if probe_url "http://127.0.0.1/"; then
    log "前端首页可访问：http://127.0.0.1/"
  else
    log "警告：前端首页 http://127.0.0.1/ 暂不可达（后端可能仍在预热）。"
  fi
  if probe_url "http://127.0.0.1/captchaImage"; then
    log "后端验证码可访问：http://127.0.0.1/captchaImage"
  else
    log "警告：后端验证码 http://127.0.0.1/captchaImage 暂不可达。"
  fi

  print_summary 0
}

# 部署汇总
print_summary() {
  local rc="$1"
  local elapsed=$(( $(date +%s) - START_TS ))
  log "===== 部署汇总 ====="
  if [[ "$rc" -eq 0 ]]; then
    log "[成功] 部署完成（目标环境：$ENV）。"
  else
    log "[失败] 部署未成功（目标环境：$ENV）。" >&2
  fi
  log "总耗时：${elapsed} 秒"
  log "访问地址清单："
  log "  前端：http://localhost/"
  log "  后端验证码：http://localhost/captchaImage"
  log "  班级网站：http://localhost/607/ 、 http://localhost/902/"
  if [[ "$ENV" == "dev" ]]; then
    log "  后端直连：http://localhost:8080/（dev 环境暴露）"
  else
    log "  后端直连：未暴露（prod/staging 不暴露 8080）"
  fi
  if [[ "$OLLAMA_STATUS" == "skipped" ]]; then
    log "Ollama：已跳过（--skip-ollama），向量化功能不可用。" >&2
  elif [[ "$OLLAMA_STATUS" == "failed" ]]; then
    log "Ollama：准备失败，向量化功能不可用；其余服务已正常启动。" >&2
  fi
}

# ---------- 主流程 ----------
main() {
  resolve_env

  # staging 前置配置闸门：缺少 compose / example 必须明确失败并给出指引
  if [[ "$ENV" == "staging" ]]; then
    if [[ ! -f docker-compose.staging.yml || ! -f .env.staging.example ]]; then
      log "错误：staging 环境尚未配置：请在仓库提供 docker-compose.staging.yml 与 .env.staging.example（可复制 prod 模板后改端口/镜像），再运行本命令。" >&2
      exit 1
    fi
  fi

  if [[ "$DRY_RUN" -eq 0 ]]; then
    check_prereqs
  fi
  prepare_env_files
  ensure_ssl_cert
  validate_contract
  if [[ "$DRY_RUN" -eq 1 ]]; then
    log "Dry run 通过，已准备的 env 文件（环境 $ENV）："
    local f
    for f in "${ENV_FILES[@]}"; do
      [[ -f "$f" ]] && log "  - $f"
    done
    log "（未调用 Ollama 准备，未启动 compose）"
    exit 0
  fi
  prepare_ollama
  ensure_log_paths
  start_stack
  health_and_probe
}

main "$@"
