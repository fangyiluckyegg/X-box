-- 创建Bibutong开发库
CREATE DATABASE IF NOT EXISTS bibutong_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建业务专用账号
CREATE USER IF NOT EXISTS 'bibutong_user'@'%' IDENTIFIED BY 'BuButong@Dev789';
GRANT ALL PRIVILEGES ON bibutong_dev.* TO 'bibutong_user'@'%';
FLUSH PRIVILEGES;