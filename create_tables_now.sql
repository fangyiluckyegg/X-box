-- 一次性补建 prj_dev 业务表（因 init.sql 缺 USE 导致首次初始化建表失败）。
-- 用 prj_user 账号执行：docker exec -i dev-mysql mysql -uprj_user -p"Prj@Dev789" prj_dev < create_tables_now.sql
-- 表用 IF NOT EXISTS，admin 用 WHERE NOT EXISTS 守卫，可重复执行无副作用。执行完可删除本文件。

CREATE TABLE IF NOT EXISTS user_info (
  user_id BIGINT AUTO_INCREMENT,
  user_name VARCHAR(45) NOT NULL,
  nick_name VARCHAR(45) NOT NULL,
  password VARCHAR(150) DEFAULT '',
  role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色 ADMIN/USER',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=100 COMMENT='用户信息表';

CREATE TABLE IF NOT EXISTS employee_kpi (
  id BIGINT AUTO_INCREMENT COMMENT '员工编号',
  kpi VARCHAR(100) NOT NULL COMMENT '考评结果',
  bonus VARCHAR(50) DEFAULT '' COMMENT '奖金',
  manager VARCHAR(50) NOT NULL COMMENT '考评人',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工评价管理表';

INSERT INTO user_info (user_name, nick_name, password, role)
SELECT 'admin', '管理员', '$2a$10$PzPW0yfIE7eW55U0Y3tQnec4UJ86QDlJFIiGwYCXZ73E/SU3YpfBS', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM user_info WHERE user_name = 'admin');
