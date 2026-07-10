-- ============================================================
-- C1 存量库迁移：user_info 增加 role 列，并将现有管理员置为 ADMIN
-- ============================================================
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--       故通过 information_schema 判断列是否存在，避免重复执行报错。

SET @exist = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'user_info'
      AND COLUMN_NAME  = 'role'
);

SET @sql = IF(
    @exist = 0,
    'ALTER TABLE user_info ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''USER'' COMMENT ''用户角色 ADMIN/USER''',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 将现有管理员（user_name = 'admin'）置为 ADMIN；新建库由 init.sql 直接写入
UPDATE user_info SET role = 'ADMIN' WHERE user_name = 'admin';
