<?php
/**
 * ensure_admin_hash.php
 * ------------------------------------------------------------------
 * 班级网站（902/message、902/work）admin_user 账号 bcrypt 化初始化脚本。
 *
 * 设计目标（Layer 2 永久修复）：
 *   - 替代旧的明文种子（msg='1' / work='123' / 副本=''），让 admin 登录
 *     走 login.php 的 password_verify（bcrypt）校验，彻底消除"登录后跳回" bug。
 *   - bcrypt hash 只能在 PHP 内生成，故放在 PHP 容器（docroot=Niu_Txl）里跑，
 *     由 compose command 在 apache 启动前调用，下次 down -v 重建自动生效。
 *   - 幂等：可重复执行；已是正确的 bcrypt hash 就不动，避免每次重启都重算。
 *
 * 调用方式：
 *   CLI 守卫：非 CLI（PHP_SAPI !== 'cli'）直接 403，防止 web 暴露被滥用。
 *   正常：php /var/www/html/ensure_admin_hash.php
 *
 * 退出码：
 *   0 = 成功（含"已是对的、跳过"）
 *   1 = 环境缺失 / DB 错误（让 apache 启动失败，便于 compose 健康检查捕获）
 * ------------------------------------------------------------------
 */

// 1) CLI 守卫：脚本位于 docroot 内会被 web 暴露，禁止通过 HTTP 访问
if (PHP_SAPI !== 'cli') {
    http_response_code(403);
    exit;
}

// 2) 读取环境变量
$host = getenv('CLASS_DB_HOST') ?: 'mysql';
$user = getenv('CLASS_DB_USER') ?: 'class_user';

$pwd = getenv('CLASS_DB_PWD');
if ($pwd === false || $pwd === '') {
    error_log('[ensure_admin_hash] CLASS_DB_PWD is missing or empty; refuse to run');
    exit(1);
}

// 新 env：admin 初始口令；默认 Admin@2026（dev），prod 应在对应 env 文件覆盖
$adminPwd = getenv('ADMIN_INIT_PWD') ?: 'Admin@2026';

// 显式遍历两个库，不读 CLASS_DB_NAME（单一 php 容器该变量可能只指向一个库）
$databases = ['msg', 'work'];

// bcrypt 前缀集合：已是正确的 hash 则跳过（幂等）
$bcryptPrefixes = ['$2y$', '$2a$', '$2b$'];

/**
 * 带边界重试的 MySQL 连接。
 *
 * dev 栈重建后 MySQL 启动约需 22s 才 listen 3306，而本脚本在 apache 启动前运行。
 * PHP 8.2 mysqli 默认 MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT，连不上会抛
 * mysqli_sql_exception 致命错导致容器 Exit 255。这里捕获该异常并做有界重试，
 * 等 MySQL listen 后再连；最终失败 exit(1) 让 compose 知道 apache 不该起来。
 *
 * @param string $host        数据库主机
 * @param string $user        用户名
 * @param string $pwd         口令
 * @param string $db          目标库名
 * @param int    $maxAttempts 最大尝试次数（默认 60）
 * @param int    $delaySec    每次重试间隔秒（默认 1）
 * @return mysqli             成功返回 mysqli 连接对象
 */
function connectWithRetry($host, $user, $pwd, $db, $maxAttempts = 60, $delaySec = 1) {
    $lastErr = null;
    for ($i = 1; $i <= $maxAttempts; $i++) {
        $conn = null;
        try {
            $conn = mysqli_connect($host, $user, $pwd, $db);
        } catch (mysqli_sql_exception $e) {
            $lastErr = $e->getMessage();
            $conn = null;
        }
        if ($conn instanceof mysqli) {
            if ($i > 1) {
                error_log("[ensure_admin_hash] mysql ready after {$i} attempt(s) for `{$db}`");
            }
            return $conn;
        }
        if ($lastErr === null) {
            $lastErr = mysqli_connect_error() ?: 'unknown error';
        }
        if ($i === 1) {
            error_log("[ensure_admin_hash] waiting for mysql ({$host}) to accept connections (db=`{$db}`)...");
        } elseif ($i % 10 === 1) {
            $preview = substr($lastErr, 0, 120);
            error_log("[ensure_admin_hash] still waiting for mysql... attempt {$i}/{$maxAttempts}, last err: {$preview}");
        }
        sleep($delaySec);
    }
    $preview = substr($lastErr ?: 'unknown', 0, 200);
    error_log("[ensure_admin_hash] gave up waiting for mysql after {$maxAttempts} attempts (db=`{$db}`): {$preview}");
    exit(1);
}

foreach ($databases as $db) {
    $conn = connectWithRetry($host, $user, $pwd, $db);
    mysqli_set_charset($conn, 'utf8');

    // 确保表存在（initdb.d 已建，这里再用 IF NOT EXISTS 兜底，避免极端时序问题）
    $createSql = 'CREATE TABLE IF NOT EXISTS `admin_user` (' .
        '`username` varchar(20) NOT NULL, ' .
        '`password` varchar(255) NOT NULL' .
        ') ENGINE=InnoDB DEFAULT CHARSET=utf8';
    if (!mysqli_query($conn, $createSql)) {
        error_log("[ensure_admin_hash] CREATE TABLE failed on `{$db}`: " . mysqli_error($conn));
        mysqli_close($conn);
        exit(1);
    }

    // 查现有 admin 账号
    $sel = "SELECT `password` FROM `admin_user` WHERE `username` = 'admin' LIMIT 1";
    $res = mysqli_query($conn, $sel);
    if ($res === false) {
        error_log("[ensure_admin_hash] SELECT failed on `{$db}`: " . mysqli_error($conn));
        mysqli_close($conn);
        exit(1);
    }

    $hash = password_hash($adminPwd, PASSWORD_DEFAULT);
    if ($hash === false || $hash === null) {
        error_log("[ensure_admin_hash] password_hash() failed on `{$db}`");
        mysqli_free_result($res);
        mysqli_close($conn);
        exit(1);
    }

    $row = mysqli_fetch_assoc($res);
    mysqli_free_result($res);

    if ($row === null) {
        // 不存在 → 插入
        $ins = "INSERT INTO `admin_user` (`username`, `password`) VALUES ('admin', '" .
            mysqli_real_escape_string($conn, $hash) . "')";
        if (!mysqli_query($conn, $ins)) {
            error_log("[ensure_admin_hash] INSERT failed on `{$db}`: " . mysqli_error($conn));
            mysqli_close($conn);
            exit(1);
        }
        error_log("[ensure_admin_hash] `{$db}`.admin_user created with bcrypt hash");
    } else {
        $existing = $row['password'];
        $isHash = false;
        foreach ($bcryptPrefixes as $p) {
            if (strncmp($existing, $p, strlen($p)) === 0) {
                $isHash = true;
                break;
            }
        }
        if ($isHash) {
            // 已是正确的 bcrypt hash → 跳过（幂等）
            error_log("[ensure_admin_hash] `{$db}`.admin_user already has bcrypt hash; skip");
        } else {
            // 明文/占位 → 更新为 bcrypt
            $upd = "UPDATE `admin_user` SET `password` = '" .
                mysqli_real_escape_string($conn, $hash) .
                "' WHERE `username` = 'admin'";
            if (!mysqli_query($conn, $upd)) {
                error_log("[ensure_admin_hash] UPDATE failed on `{$db}`: " . mysqli_error($conn));
                mysqli_close($conn);
                exit(1);
            }
            error_log("[ensure_admin_hash] `{$db}`.admin_user password upgraded to bcrypt hash");
        }
    }

    mysqli_close($conn);
}

error_log('[ensure_admin_hash] done: admin_user bcrypt ensure completed for msg/work');
exit(0);
