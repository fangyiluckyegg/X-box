#!/bin/sh
# ============================================================
# docker-entrypoint-llama.sh
# [C15] Ollama 启动包装脚本：容器每次启动时幂等拉取业务模型
# bge-m3:latest，消除"容器在跑但模型未拉取 → /api/embeddings 404 →
# 前端报'全部向量生成失败'"的运行时故障。
#
# 设计要点（对齐 db/mysql_scripts/docker-entrypoint-wrapper.sh 风格）：
#   1. 后台启动官方 `ollama serve`，脚本前台 wait，保证 docker stop
#      优雅退出（信号转发给 serve 进程）。
#   2. 轮询等待服务就绪（ollama list 退出 0 视为就绪），可被信号/超时/早退中断。
#   3. 若模型不存在则前台 ollama pull（带重试与单次超时）。
#   4. 拉取全部失败：写标记文件 /tmp/ollama-model-unavailable，继续服役，
#      不崩溃（后端仍能启动，业务层给出"模型不可用"提示而非死锁）。
#   5. 所有探针/检查通过 OLLAMA_HOST=<loopback> 前缀执行
#      （serve 绑定 0.0.0.0，127.0.0.1 可达，比 0.0.0.0 探针更稳）。
#
# 注意：使用 POSIX /bin/sh 编写（ollama/ollama:0.31.2 基于 Alpine，
#       不保证有 bash，禁用 bash 专属语法如 set -o pipefail、${VAR//}）。
#       刻意不写进构建期（RUN ollama pull），避免镜像构建卡住，
#       符合原 Dockerfile 不自动 pull 的初衷。
# ============================================================

set -eu

# ---------------------- 工具函数 ----------------------
log() {
    echo "[entrypoint-llama] $(date '+%Y-%m-%d %H:%M:%S') $*"
}

# 去除尾随 \r（防御 Windows CRLF 经环境变量注入，与团队 CRLF 修复主题一致；
# 使用 tr 而非 ${VAR//} 以保持 POSIX sh 兼容）。
strip_cr() {
    printf '%s' "$1" | tr -d '\r'
}

# 运行带超时的命令；若系统无 timeout 命令（极少见），退化到直接执行，
# 避免拉取卡死导致容器无法进入服役状态。
run_with_timeout() {
    _to="$1"; shift
    if command -v timeout >/dev/null 2>&1; then
        timeout "$_to" "$@"
    else
        "$@"
    fi
}

# ---------------------- 可覆盖配置（均带默认值） ----------------------
OLLAMA_BIN="${OLLAMA_BIN:-ollama}"
# 模型名：优先 MODEL_NAME（测试/覆盖用），其次 compose 的 OLLAMA_MODEL（单一来源），
# 最终兜底 bge-m3:latest，与后端 CompareController 写死的模型名保持一致。
MODEL_NAME="${MODEL_NAME:-${OLLAMA_MODEL:-bge-m3:latest}}"
# 容器内探针用 loopback 比 0.0.0.0 稳（serve 绑定 0.0.0.0，127.0.0.1 可达）。
OLLAMA_PROBE_HOST="${OLLAMA_PROBE_HOST:-127.0.0.1:11434}"
# 等待 Ollama 就绪的最大秒数。
MAX_WAIT="${MAX_WAIT:-120}"
# 模型拉取失败（如离线）时的最大重试次数。
PULL_RETRIES="${PULL_RETRIES:-3}"
# 单次拉取超时秒数（bge-m3 约 1.2GB，需足够大）。
PULL_TIMEOUT="${PULL_TIMEOUT:-600}"
# 模型不可用标记文件路径（healthcheck 据此放行后端启动）。
MODEL_UNAVAILABLE_MARKER="${MODEL_UNAVAILABLE_MARKER:-/tmp/ollama-model-unavailable}"

# 防御性 CRLF 剥离：compose environment 直传理论上干净，但团队存在 .env CRLF
# 污染先例，这里统一兜底，避免模型名/主机名带 \r 导致探针或 pull 静默失败。
OLLAMA_BIN="$(strip_cr "$OLLAMA_BIN")"
MODEL_NAME="$(strip_cr "$MODEL_NAME")"
OLLAMA_PROBE_HOST="$(strip_cr "$OLLAMA_PROBE_HOST")"
MAX_WAIT="$(strip_cr "$MAX_WAIT")"
PULL_RETRIES="$(strip_cr "$PULL_RETRIES")"
PULL_TIMEOUT="$(strip_cr "$PULL_TIMEOUT")"
MODEL_UNAVAILABLE_MARKER="$(strip_cr "$MODEL_UNAVAILABLE_MARKER")"

# ---------------------- 运行时状态 ----------------------
SERVE_PID=""
STOPPING=0

# ---------------------- 信号转发 ----------------------
# 把容器收到的终止信号转交给 ollama serve，保证 docker stop 优雅关闭。
forward_signal() {
    _sig="$1"
    if [ -n "$SERVE_PID" ] && kill -0 "$SERVE_PID" 2>/dev/null; then
        log "收到信号 ${_sig}，转发给 ollama serve (PID=${SERVE_PID}) 以触发优雅关闭。"
        # shellcheck disable=SC2086
        kill "-${_sig}" "$SERVE_PID" 2>/dev/null || true
    fi
    STOPPING=1
}

# ---------------------- 等待就绪 ----------------------
# 轮询直到 ollama list 成功（退出 0 视为就绪）。支持被信号中断、serve 早退、超时。
wait_for_ready() {
    log "等待 Ollama 服务就绪（最多 ${MAX_WAIT}s，探针 host=${OLLAMA_PROBE_HOST}）..."
    _waited=0
    while [ "$_waited" -lt "$MAX_WAIT" ]; do
        if [ "$STOPPING" = "1" ]; then
            log "收到停止信号，中止等待就绪。"
            return 1
        fi
        if ! kill -0 "$SERVE_PID" 2>/dev/null; then
            log "ERROR: ollama serve 在就绪前已退出，请检查容器日志。"
            return 1
        fi
        if OLLAMA_HOST="$OLLAMA_PROBE_HOST" "$OLLAMA_BIN" list >/dev/null 2>&1; then
            log "Ollama 服务已就绪。"
            return 0
        fi
        sleep 2
        _waited=$((_waited + 2))
    done
    log "ERROR: 等待 Ollama 就绪超时（${MAX_WAIT}s）。"
    return 1
}

# ---------------------- 拉取模型 ----------------------
# 前台拉取模型，带 PULL_RETRIES 次重试，单次用 timeout 包裹。全部失败返回非 0。
pull_model() {
    _attempt=0
    while [ "$_attempt" -lt "$PULL_RETRIES" ]; do
        _attempt=$((_attempt + 1))
        log "拉取模型 ${MODEL_NAME}（第 ${_attempt}/${PULL_RETRIES} 次尝试，超时 ${PULL_TIMEOUT}s）..."
        if OLLAMA_HOST="$OLLAMA_PROBE_HOST" run_with_timeout "$PULL_TIMEOUT" "$OLLAMA_BIN" pull "$MODEL_NAME" >/dev/null 2>&1; then
            log "模型 ${MODEL_NAME} 拉取成功。"
            return 0
        fi
        log "WARN: 第 ${_attempt} 次拉取 ${MODEL_NAME} 失败，重试中..."
    done
    return 1
}

# ---------------------- 主流程 ----------------------
main() {
    # 早期安装信号陷阱，覆盖启动等待与拉取全过程
    trap 'forward_signal TERM' TERM
    trap 'forward_signal INT' INT

    # 1) 后台启动 ollama serve
    log "启动 ${OLLAMA_BIN} serve"
    "$OLLAMA_BIN" serve &
    SERVE_PID=$!
    log "ollama serve 进程 PID=${SERVE_PID}"

    # 2) 等待 Ollama 就绪（可被信号中断）
    if ! wait_for_ready; then
        kill -TERM "$SERVE_PID" 2>/dev/null || true
        wait "$SERVE_PID" 2>/dev/null || true
        exit 1
    fi

    # 3) 幂等拉取：仅当模型不存在时才 pull
    #    （配合 ./ai_llama:/root/.ollama 持久化，二次启动跳过，不重复拉取）
    if OLLAMA_HOST="$OLLAMA_PROBE_HOST" "$OLLAMA_BIN" show "$MODEL_NAME" >/dev/null 2>&1; then
        log "模型 ${MODEL_NAME} 已存在，跳过拉取（幂等）。"
    else
        log "模型 ${MODEL_NAME} 不存在，开始拉取..."
        # 拉取全部失败：写标记文件、继续服役、不崩溃（预期降级行为）
        if ! pull_model; then
            log "ERROR: 模型 ${MODEL_NAME} 拉取失败（可能离线）。写入标记文件 ${MODEL_UNAVAILABLE_MARKER}，继续服役。"
            touch "$MODEL_UNAVAILABLE_MARKER" 2>/dev/null || true
        fi
    fi

    # 4) 前台阻塞，直到 serve 退出（期间信号会经陷阱转发）
    log "模型就绪检查完成，进入前台等待 ollama serve 退出..."
    set +e
    wait "$SERVE_PID"
    _exit_code=$?
    set -e
    if [ -f "$MODEL_UNAVAILABLE_MARKER" ]; then
        log "ollama serve 退出（曾降级运行：模型不可用），退出码=${_exit_code}"
    else
        log "ollama serve 退出，退出码=${_exit_code}"
    fi
    exit "$_exit_code"
}

main "$@"
