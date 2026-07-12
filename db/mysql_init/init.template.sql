-- ============================================================
-- 初始化脚本模板（db/mysql_init/init.template.sql）
-- 职责：init.sql 的模板版本，业务账号密码以占位符 DEV_DB_PASSWORD_PLACEHOLDER 标识，
--       供本地自行替换；user_info 表此处尚未含 role 列（角色列由 migrate_role.sql 补齐）。
-- 说明：Bibutong 相关建库/建账号语句已注释（保留为可选扩展）。
-- ============================================================

#-- 创建Bibutong开发库
#CREATE DATABASE IF NOT EXISTS bibutong_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
#
#-- 创建Bibutong业务专用账号（密码占位符，本地自行替换）
#CREATE USER IF NOT EXISTS 'bibutong_user'@'%' IDENTIFIED BY 'DEV_DB_PASSWORD_PLACEHOLDER';
#GRANT ALL PRIVILEGES ON bibutong_dev.* TO 'bibutong_user'@'%';
#FLUSH PRIVILEGES;

-- 创建Prj开发库
CREATE DATABASE IF NOT EXISTS prj_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建Prj业务专用账号（密码占位符，本地自行替换）
CREATE USER IF NOT EXISTS 'prj_user'@'%' IDENTIFIED BY 'DEV_DB_PASSWORD_PLACEHOLDER';
GRANT ALL PRIVILEGES ON prj_dev.* TO 'prj_user'@'%';
FLUSH PRIVILEGES;

CREATE TABLE user_info (
  user_id BIGINT AUTO_INCREMENT,
  user_name VARCHAR(45) NOT NULL,
  nick_name VARCHAR(45) NOT NULL,
  password VARCHAR(150) DEFAULT '',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=100 COMMENT='用户信息表';
