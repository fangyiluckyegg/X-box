-- ============================================================
-- 数据迁移脚本：msg / work 库及表由 utf8 升级为 utf8mb4
-- 文件：db/mysql_init/zz_migrate_msg_work_utf8mb4.sql
-- ============================================================
-- 用途（R-10 / P1 字符集闭环）：
--   Niu_Txl 的 msg、work 两个业务库，其建表 SQL（db/mysql_init/msg.sql、
--   db/mysql_init/work.sql，以及参考副本 Niu_Txl/902/msg.sql、Niu_Txl/902/work.sql）
--   此前使用 DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci。连接层（SET NAMES utf8mb4）
--   与 PHP 代码早已改为 utf8mb4，但【建表字符集】仍为 utf8，导致 4 字节字符（emoji 等）
--   写入时报 Incorrect string value。本脚本用于把【已存在运行库】中的表原地升级到 utf8mb4，
--   与新建库（已改 utf8mb4）保持一致，彻底闭环字符集问题。
--
-- 覆盖范围（下表名均取自上述建表 SQL，已逐一核对，非猜测）：
--   msg   库：admin_user、post、reply
--   work  库：admin_user、work_pic、work_type
-- COLLATE 选择：utf8mb4_general_ci（与建表 SQL 原 utf8_general_ci 一一对应升级，保持库内一致）。
--
-- 执行顺序约束（与既有 zz_migrate_utf8mb4.sql 同理）：
--   本脚本 ALTER 的是 msg / work 库及其表，必须在 msg.sql / work.sql 建好这两个库之后才能运行。
--   文件名加 zz_ 前缀（字母序排在 init / migrate_role / msg / work 之后），确保 MySQL 官方
--   entrypoint 按字母序【最后】执行；不可用数字前缀（数字在字母序里排最前，会反而最先执行，
--   报 "Database 'msg' doesn't exist" 并中断整个 initdb 批处理）。
--   若这些表实际不存在于当前运行库（即仅为建表模板、尚未建过库），本脚本运行会报
--   "Table doesn't exist"——属预期，可忽略或仅作留档，不影响建表流程。
--
-- 执行方式（在 dev-mysql / 共享 dev-mysql 容器中，针对已存在的库）：
--   mysql -u root -p < db/mysql_init/zz_migrate_msg_work_utf8mb4.sql
-- 或经 MySQL 客户端分别 USE msg / USE work 后运行。
-- 注意：CONVERT TO 会重写表数据；大表请在低峰期执行并先备份。
-- 连接层已在 msg.sql / work.sql 顶部 SET NAMES utf8mb4，无需改 PHP 代码。
-- ============================================================

-- 1) msg 库
ALTER DATABASE `msg` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;
USE `msg`;
ALTER TABLE `admin_user` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `post`       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `reply`      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 2) work 库
ALTER DATABASE `work` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;
USE `work`;
ALTER TABLE `admin_user` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `work_pic`    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `work_type`   CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
