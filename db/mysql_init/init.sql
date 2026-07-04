-- 创建Bibutong开发库
#CREATE DATABASE IF NOT EXISTS bibutong_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
#-- 创建Bibutong业务专用账号
#CREATE USER IF NOT EXISTS 'bibutong_user'@'%' IDENTIFIED BY 'BuButong@Dev789';
#GRANT ALL PRIVILEGES ON bibutong_dev.* TO 'bibutong_user'@'%';
#FLUSH PRIVILEGES;


-- 创建Prj开发库
CREATE DATABASE IF NOT EXISTS prj_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 创建Prj业务专用账号
CREATE USER IF NOT EXISTS 'prj_user'@'%' IDENTIFIED BY 'Prj@Dev789';
GRANT ALL PRIVILEGES ON prj_dev.* TO 'prj_user'@'%';
FLUSH PRIVILEGES;

CREATE TABLE user_info (
  user_id NUMBER(20) GENERATED ALWAYS AS IDENTITY (START WITH 100 INCREMENT BY 1),
  user_name VARCHAR2(45) NOT NULL,
  nick_name VARCHAR2(45) NOT NULL,
  password VARCHAR2(150) DEFAULT '',
  CONSTRAINT pk_user_info PRIMARY KEY (user_id)
);

COMMENT ON TABLE user_info IS '用户信息表';