#!/bin/bash
# ============================================================
# docker-entrypoint-wrapper.sh
# C1 存量库漏列修复：包装 MySQL 官方 docker-entrypoint.sh，
# 使 MySQL 在【每次启动】（含存量库 volume 不清的场景）都幂等补
# user_info.role 列，杜绝存量库升级路径漏列导致登录报"联系系统管理员"。
#
# 设计要点：
#   1. 后台启动官方 docker-entrypoint.sh（其最终 exec mysqld），
#      保证首次初始化、字符集参数等官方行为完全不变。
#   2. 通过 mysqladmin ping 循环等待 MySQL 真正就绪，再执行迁移。
#   3. 以环境变量中的凭据执行 /docker-entrypoint-initdb.d/migrate_role.sql
#      （脚本本身已用 information_schema 判断列是否存在，可重复执行无害）。
#   4. 正确转发 SIGTERM/SIGINT 给 mysqld，保证 docker stop 优雅关闭
#      （innodb 正常刷盘），不破坏官方镜像的优雅退出语义。
#   5. 启动失败 / 超时 / 进程早退均有清晰报错并带非 0 退出码。
#   6. 自愈应用账号 host 授权（ensure_app_user）：每次启动在 MySQL 就绪后、
#      迁移前对 prj_user@'%' 做幂等 CREATE USER + GRANT，规避持久化数据卷
#      未重跑 initdb 导致账号 host 受限（Host 'X' is not allowed to connect）。
#      不修改任何业务数据，失败仅告警、不阻断主流程。
#
# 注意：本脚本【不】放在 /docker-entrypoint-initdb.d 下，避免首次初始化时
#       被官方 entrypoint 当作初始化脚本误执行（彼时 MySQL 尚未就绪）。
# ============================================================

# 严格模式：未定义变量报错、管道任一环节失败即报错
set -uo pipefail

# ---------------------- 可覆盖的配置（均带默认值） ----------------------
# 官方 entrypoint 绝对路径（mysql:8.0 镜像固定位置）
ENTRYPOINT_OFFICIAL="${ENTRYPOINT_OFFICIAL:-/usr/local/bin/docker-entrypoint.sh}"
# 待执行的幂等迁移脚本（挂载在 initdb.d 下）
MIGRATE_SQL="${MIGRATE_SQL:-/docker-entrypoint-initdb.d/migrate_role.sql}"
# 目标数据库名（migrate_role.sql 用 DATABASE() 取当前库，必须显式指定）
MYSQL_DATABASE_NAME="${MYSQL_DATABASE:-${DB_NAME:-prj_dev}}"
# MySQL 本地 socket 路径（root@localhost 走 socket 最可靠，无需依赖 TCP 授权）
MYSQL_SOCKET="${MYSQL_SOCKET:-/var/run/mysqld/mysqld.sock}"
# 等待 MySQL 就绪的最大秒数
MAX_WAIT="${MYSQL_READY_TIMEOUT:-120}"
# 迁移执行失败（如目标库尚未创建）时的最大重试次数
MIGRATE_MAX_RETRIES="${MIGRATE_MAX_RETRIES:-30}"

# ---------------------- 运行时状态 ----------------------
MYSQL_PID=""
STOPPING=0

# ---------------------- 工具函数 ----------------------
log() {
    echo "[entrypoint-wrapper] $(date '+%Y-%m-%d %H:%M:%S') $*"
}

# 生成临时 my.cnf，避免密码在 `ps` 中暴露。
# 优先使用 MYSQL_ROOT_PASSWORD 走 socket 连接 root@localhost；
# 若未设置 root 密码，则回退到 PRJ_DB_USER/PRJ_DB_PWD 走 TCP。
# 可选位置参数 $1=no_db：传入任意非空值则省略 database= 行，
# 使探针只检测服务器就绪、不依赖目标库已存在（避免就绪探测误判触发重启）。
write_my_cnf() {
    local cnf
    # [FIX] 剥除 Windows CRLF 带入的尾随 \r：.env.dev 若为 CRLF，
    # MYSQL_ROOT_PASSWORD 会带 \r，导致生成的 my.cnf 中密码包含 \r，
    # mysqladmin ping 用脏密码鉴权失败、始终超时，进而 ensure_app_user
    # 永不执行、后端报 Host not allowed。与 ensure_app_user 保持一致。
    local root_pwd="${MYSQL_ROOT_PASSWORD:-}"; root_pwd="${root_pwd//$'\r'/}"
    # 可选位置参数 no_db：传入任意非空值则【省略】database= 行，
    # 用于 mysqladmin ping 就绪探针（只关心服务器是否就绪，不依赖具体库存在）。
    local no_db="${1:-}"
    local db_line="database=${MYSQL_DATABASE_NAME}"
    if [ -n "$no_db" ]; then
        db_line=""
    fi
    cnf="$(mktemp /tmp/mysql-migrate.XXXXXX.cnf)"
    chmod 600 "$cnf"
    if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
        cat > "$cnf" <<EOF
[client]
socket=${MYSQL_SOCKET}
user=root
password=${root_pwd}
${db_line}
EOF
    elif [ -n "${PRJ_DB_PWD:-}" ]; then
        cat > "$cnf" <<EOF
[client]
host=127.0.0.1
port=3306
user=${PRJ_DB_USER:-prj_user}
password=${PRJ_DB_PWD}
${db_line}
EOF
    else
        cat > "$cnf" <<EOF
[client]
socket=${MYSQL_SOCKET}
user=root
${db_line}
EOF
    fi
    echo "$cnf"
}

# 等待 MySQL 就绪（mysqladmin ping 成功即视为就绪）。
# 支持被信号中断（STOPPING）、检测 mysqld 早退、超时退出。
wait_for_mysql() {
    local cnf waited=0
    log "等待 MySQL 就绪（最多 ${MAX_WAIT}s）..."
    while true; do
        if [ "${STOPPING:-0}" = "1" ]; then
            log "收到停止信号，中止等待。"
            return 1
        fi
        if ! kill -0 "$MYSQL_PID" 2>/dev/null; then
            log "ERROR: MySQL 进程在就绪前已退出，请检查容器日志。"
            return 1
        fi
        cnf="$(write_my_cnf no_db)"
        if mysqladmin --defaults-extra-file="$cnf" ping >/dev/null 2>&1; then
            rm -f "$cnf"
            log "MySQL 已就绪。"
            return 0
        fi
        rm -f "$cnf"
        if [ "$waited" -ge "$MAX_WAIT" ]; then
            log "ERROR: 等待 MySQL 就绪超时（${MAX_WAIT}s），放弃执行迁移。"
            return 1
        fi
        sleep 2
        waited=$((waited + 2))
    done
}

# 执行幂等迁移（带重试，兼容首次初始化时目标库尚未建好的时序）。
run_migration() {
    if [ ! -f "$MIGRATE_SQL" ]; then
        log "WARN: 迁移脚本不存在：${MIGRATE_SQL}，跳过迁移。"
        return 0
    fi
    local cnf attempt=0 err
    while [ "$attempt" -lt "$MIGRATE_MAX_RETRIES" ]; do
        attempt=$((attempt + 1))
        cnf="$(write_my_cnf)"
        if mysql --defaults-extra-file="$cnf" < "$MIGRATE_SQL" >/dev/null 2>/tmp/migrate_err; then
            rm -f "$cnf"
            log "迁移执行成功（第 ${attempt} 次尝试）。"
            return 0
        fi
        err="$(cat /tmp/migrate_err 2>/dev/null || true)"
        rm -f "$cnf"
        # “目标库尚不存在”属可重试错误：首次初始化时官方 entrypoint 可能还在建库
        if echo "$err" | grep -qi "Unknown database"; then
            log "WARN: 目标库 ${MYSQL_DATABASE_NAME} 尚不存在，第 ${attempt}/${MIGRATE_MAX_RETRIES} 次重试（2s 后）..."
            sleep 2
            continue
        fi
        log "ERROR: 迁移执行失败：${err}"
        return 1
    done
    log "ERROR: 迁移在 ${MIGRATE_MAX_RETRIES} 次尝试后仍失败，请人工排查。"
    return 1
}

# 自愈：确保应用账号 prj_user@'%' 存在并拥有库授权。
# 解决持久卷未重跑 initdb 导致账号 host 受限（Host 'X' is not allowed to connect）。
# 每次启动执行，幂等，不依赖 initdb；不修改任何业务数据。
ensure_app_user() {
    local cnf
    local app_user="${SPRING_DATASOURCE_USERNAME:-prj_user}"
    local app_pwd="${SPRING_DATASOURCE_PASSWORD:-Prj@Dev789}"
    local app_db="${MYSQL_DATABASE_NAME}"
    # [FIX] 剥除 Windows CRLF 带入的尾随 \r：.env.dev 若为 CRLF，
    # SPRING_DATASOURCE_USERNAME/PASSWORD 会带 \r，导致建出 'prj_user\r'@'%'
    # 而非 'prj_user'@'%'，后端用干净用户名连接时被拒（Host not allowed）。
    app_user="${app_user//$'\r'/}"
    app_pwd="${app_pwd//$'\r'/}"
    app_db="${app_db//$'\r'/}"
    cnf="$(write_my_cnf)"
    log "确保应用账号 ${app_user}@'%' 存在并授权 ${app_db}.* ..."
    if mysql --defaults-extra-file="$cnf" <<SQL
CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED WITH caching_sha2_password BY '${app_pwd}';
ALTER USER '${app_user}'@'%' IDENTIFIED WITH caching_sha2_password BY '${app_pwd}';
# [T10] 显式指定 caching_sha2_password 认证插件，规避已废弃的 mysql_native_password
# （MySQL 8.0 启动/连接时会报 MY-013360 弃用告警）。后端 JDBC 走 useSSL=true（TLS），
# caching_sha2_password 在 TLS 下无需服务端公钥即可完成握手，安全且兼容；
# 既存 native 账号经 ALTER 自动升级为 caching_sha2，新建账号亦直接落 caching_sha2，杜绝反复告警。
# 口令同步语义保留：CREATE USER 对既存用户不更新口令，故下方 ALTER 仍负责把 .env 新口令同步到库，
# 解决共享 dev-mysql 的 prod/后端口令漂移。用户已由上两行保证存在，ALTER 永不报"用户不存在"。
ALTER USER '${app_user}'@'%' IDENTIFIED WITH caching_sha2_password BY '${app_pwd}';
GRANT ALL PRIVILEGES ON ${app_db}.* TO '${app_user}'@'%';
FLUSH PRIVILEGES;
SQL
    then
        rm -f "$cnf"
        log "应用账号授权完成（${app_user}@'%' -> ${app_db}.*）。"
        return 0
    fi
    rm -f "$cnf"
    log "WARN: 应用账号授权失败，请人工排查（不影响 MySQL 运行）。"
    return 1
}

# 信号转发：把容器收到的终止信号转交给 mysqld，保证优雅关闭。
forward_signal() {
    local sig="$1"
    if [ -n "$MYSQL_PID" ] && kill -0 "$MYSQL_PID" 2>/dev/null; then
        log "收到信号 ${sig}，转发给 MySQL (PID=${MYSQL_PID}) 以触发优雅关闭。"
        # shellcheck disable=SC2086
        kill "-${sig}" "$MYSQL_PID" 2>/dev/null || true
    fi
    STOPPING=1
}

# ---------------------- 主流程 ----------------------
main() {
    # 早期安装信号陷阱，覆盖启动等待与迁移全过程
    trap 'forward_signal TERM' TERM
    trap 'forward_signal INT' INT

    # 1) 后台启动官方 entrypoint（其最终会 exec mysqld，参数来自 compose 的 command）
    log "启动官方 docker-entrypoint.sh $*"
    "$ENTRYPOINT_OFFICIAL" "$@" &
    MYSQL_PID=$!
    log "MySQL 进程 PID=${MYSQL_PID}"

    # 2) 等待 MySQL 就绪（可被信号中断）
    if ! wait_for_mysql; then
        kill -TERM "$MYSQL_PID" 2>/dev/null || true
        wait "$MYSQL_PID" 2>/dev/null || true
        exit 1
    fi

    # 2.5) 自愈应用账号 host 授权（幂等，解决 Host not allowed）
    ensure_app_user || true

    # 3) 执行幂等迁移（失败不阻断主进程，但记录告警，便于人工排查）
    if ! run_migration; then
        log "WARN: 迁移未成功完成，但 MySQL 仍在运行；请人工排查。"
    fi

    # 4) 前台阻塞，直到 mysqld 退出（期间信号会经陷阱转发）
    log "迁移完成，进入前台等待 MySQL 进程退出..."
    set +e
    wait "$MYSQL_PID"
    EXIT_CODE=$?
    set -e
    log "MySQL 进程退出，退出码=${EXIT_CODE}"
    exit "$EXIT_CODE"
}

main "$@"
