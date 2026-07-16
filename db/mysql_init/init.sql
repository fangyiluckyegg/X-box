-- ============================================================
-- 初始化脚本：Prj 开发库（db/mysql_init/init.sql）
-- 职责：创建 prj_dev 数据库、业务账号 prj_user，并建 user_info / employee_kpi 表，
--       写入默认管理员 admin。由 MySQL 官方镜像在首次初始化时自动执行（initdb.d）。
-- 说明：含 [C17] 显式 USE、[P0-FIX] 开发默认密码、[P1-15-FIX] Oracle→MySQL 语法修正等历史修复标注。
-- ============================================================



-- 创建Prj开发库
CREATE DATABASE IF NOT EXISTS prj_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- [C17] 显式选中目标库：官方 entrypoint 跑 initdb.d 时不保证自动 USE $MYSQL_DATABASE，
-- 若缺这句，后续 CREATE TABLE 会因 "No database selected" 失败，导致库表/账号均未建出。
USE prj_dev;
-- ============================================================
-- [O1-去密] 应用账号 prj_user 的创建与授权已【不再】写在本文件（原第 25-27 行已删除）。
--   原因：本 init.sql 由 dev-mysql 容器在首次初始化时执行；而 dev-mysql 在每次启动时，
--   按【MySQL 容器 env】(.env.dev) 的 SPRING_DATASOURCE_PASSWORD，
--   由 db/mysql_scripts/docker-entrypoint-wrapper.sh 的 ensure_app_user() 幂等注入 prj_user 口令。
--   若本文件写死口令，会造成 dev / prod 口令漂移，违背 O1"安全去密"目标。
--   因此本文件不含任何明文口令；prj_user 账号及其授权全部运行时注入。
-- ============================================================

-- [P1-15-FIX] 修正Oracle语法为MySQL 8语法
-- NUMBER -> BIGINT/INT, VARCHAR2 -> VARCHAR, GENERATED ALWAYS AS IDENTITY -> AUTO_INCREMENT
CREATE TABLE user_info (
  user_id BIGINT AUTO_INCREMENT,
  user_name VARCHAR(45) NOT NULL,
  nick_name VARCHAR(45) NOT NULL,
  password VARCHAR(150) DEFAULT '',
  -- [C1] 最小角色列：仅存单一角色字面量 ADMIN / USER
  role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色 ADMIN/USER',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=100 COMMENT='用户信息表';

-- [P1-15-FIX] employee_kpi 表 DDL（字段与 EmployeeKpi.java 一致）
CREATE TABLE employee_kpi (
  id BIGINT AUTO_INCREMENT COMMENT '员工编号',
  kpi VARCHAR(100) NOT NULL COMMENT '考评结果',
  bonus VARCHAR(50) DEFAULT '' COMMENT '奖金',
  manager VARCHAR(50) NOT NULL COMMENT '考评人',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工评价管理表';

-- [P2-13-FIX] 默认管理员账号（密码 admin123 的 BCrypt 哈希）
-- [C1] role='ADMIN'：保证管理员在 @PreAuthorize("hasRole('ADMIN')") 与 /druid/** 判定中放行
INSERT INTO user_info (user_name, nick_name, password, role) VALUES
('admin', '管理员', '$2a$10$PzPW0yfIE7eW55U0Y3tQnec4UJ86QDlJFIiGwYCXZ73E/SU3YpfBS', 'ADMIN');
