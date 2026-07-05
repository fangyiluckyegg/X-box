-- 创建Bibutong开发库
#CREATE DATABASE IF NOT EXISTS bibutong_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
#-- 创建Bibutong业务专用账号
#CREATE USER IF NOT EXISTS 'bibutong_user'@'%' IDENTIFIED BY 'BuButong@Dev789';
#GRANT ALL PRIVILEGES ON bibutong_dev.* TO 'bibutong_user'@'%';
#FLUSH PRIVILEGES;


-- 创建Prj开发库
CREATE DATABASE IF NOT EXISTS prj_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- [P0-FIX] 以下为开发环境默认密码，生产部署后必须执行：
--   ALTER USER 'prj_user'@'%' IDENTIFIED BY '<新密码>';
--   并在 application.yml 中设置 SPRING_DATASOURCE_PASSWORD 环境变量
-- 密码必须与 application.yml 的 SPRING_DATASOURCE_PASSWORD 默认值一致
CREATE USER IF NOT EXISTS 'prj_user'@'%' IDENTIFIED BY 'Prj@Dev789';
GRANT ALL PRIVILEGES ON prj_dev.* TO 'prj_user'@'%';
FLUSH PRIVILEGES;

-- [P0-FIX] 修正Oracle语法为MySQL 8语法
-- NUMBER -> BIGINT/INT, VARCHAR2 -> VARCHAR, GENERATED ALWAYS AS IDENTITY -> AUTO_INCREMENT
CREATE TABLE user_info (
  user_id BIGINT AUTO_INCREMENT,
  user_name VARCHAR(45) NOT NULL,
  nick_name VARCHAR(45) NOT NULL,
  password VARCHAR(150) DEFAULT '',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=100 COMMENT='用户信息表';
