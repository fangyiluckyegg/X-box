<?php
// 902/message/Connections/conn.php —— 容器化改造：连接参数读环境变量
// 新增 prj-php 容器后，MySQL 通过服务名 dev-mysql 互访（见 docker-compose.prod.yml）。
// 环境变量缺失时回退到容器网络内的默认值，保证本地/CI 也能跑通。
$hostname_conn = getenv('CLASS_DB_HOST') ?: 'dev-mysql';
$database_conn = getenv('CLASS_DB_NAME') ?: 'msg';   // message 站点 → msg 库
$username_conn = getenv('CLASS_DB_USER') ?: 'class_user';
$password_conn = getenv('CLASS_DB_PWD');
if ($password_conn === false || $password_conn === '') {
    error_log('CLASS_DB_PWD is missing or empty; refusing to connect');
    http_response_code(500);
    die('服务配置缺失,无法连接数据库');
}

// PHP7.4 直连 MySQL8；连接失败立即报错，便于容器健康检查捕获
$conn = mysqli_connect($hostname_conn, $username_conn, $password_conn, $database_conn);
if (!$conn) {
    error_log('DB connect failed: ' . mysqli_connect_error());
    http_response_code(500);
    die('服务暂时不可用,请稍后重试');
}
mysqli_set_charset($conn, 'utf8');   // 与现有表字符集保持一致（utf8，非 utf8mb4）

// 自动兼容老项目函数（保留原垫片，避免旧代码 mysql_* 调用报错）
if (!function_exists('mysql_query')) {
    function mysql_query($sql) { global $conn; return mysqli_query($conn, $sql); }
    function mysql_select_db($db) { global $conn; return mysqli_select_db($conn, $db); }
    function mysql_fetch_array($res) { return mysqli_fetch_array($res); }
    function mysql_num_rows($res) { return mysqli_num_rows($res); }
    function mysql_error() { global $conn; return mysqli_error($conn); }
}
?>
