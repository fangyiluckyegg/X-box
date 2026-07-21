# X-box DB 合并运行手册（prj-mysql → dev-mysql）

> 生成日期：2026-07-14
> 范围：仅配置/拓扑合并。**不在此脚本中启动容器、不迁移数据**，所有动作须由运维/用户
> 在 `dev-mysql` 已运行（与 base.yml 一并 `up`）后手动执行。
> 配置改动见 `docs/audit/archive/2026-07-14/docs/X-box-db-merge-log-2026-07-14.md`。

## 0. 前置说明

- 决策：废弃独立 `prj-mysql`，生产全栈统一复用 `docker-compose.base.yml` 的 `dev-mysql`
  （容器名 `dev-mysql`，网络 `dev-network`）。
- `dev-mysql` 已初始化完成（卷持久化，`initdb.d` 不再重复执行），因此下列库/账号/授权/密码
  **不会**由容器自动建立，必须手动执行。
- `compose` 启动方式（dev-mysql 为常驻依赖）：

```bash
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

- 变量 `<CLASS_DB_PWD>` 一律取 `.env.prod` 中已轮换的强随机 `CLASS_DB_PWD` 实际值。
  **务必与注入容器的 `CLASS_DB_PWD` 完全一致，否则 PHP / 后端连接失败。**

---

## 1. 建库

```sql
-- 进入 dev-mysql： docker exec -it dev-mysql mysql -uroot -p
CREATE DATABASE IF NOT EXISTS prj_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS msg       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS work      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 2. 账号（二选一，推荐 A 不改代码）

### 方案 A（推荐）：新建 `class_user`@'%'

```sql
CREATE USER IF NOT EXISTS 'class_user'@'%' IDENTIFIED BY '<CLASS_DB_PWD>';
```

> 说明：PHP 默认 `class_user`@'%' 当前不存在于 dev-mysql（仅有 `prj_user`）。
> 新建后 PHP 的 `CLASS_DB_USER`/`CLASS_DB_PWD` 逻辑无需改动。

### 方案 B：复用 `prj_user`（需改 PHP 代码）

将 `Niu_Txl/902/message/Connections/conn.php` 与 `Niu_Txl/902/work/Connections/conn.php` 中
`CLASS_DB_USER` 默认值 `class_user` 改为 `prj_user`，并确保 `CLASS_DB_PWD` 与下方
`prj_user` 密码对齐。**不推荐**（改代码面大）。

---

## 3. 授权

```sql
GRANT ALL PRIVILEGES ON prj_prod.* TO 'class_user'@'%';
GRANT ALL PRIVILEGES ON msg.*       TO 'class_user'@'%';
GRANT ALL PRIVILEGES ON work.*      TO 'class_user'@'%';
FLUSH PRIVILEGES;
```

> 若选方案 B，将 `'class_user'@'%'` 替换为 `'prj_user'@'%'`。

---

## 4. 密码对齐（P0 弱口令修复）

dev-mysql 现有 `prj_user` 密码为硬编码弱口令 `Prj@Dev789`（init.sql，P0 已标记），须对齐为
`<CLASS_DB_PWD>`：

```sql
ALTER USER 'prj_user'@'%' IDENTIFIED BY '<CLASS_DB_PWD>';
-- 若选方案 A，class_user 同理（若建库后已设密码也一并对齐）：
ALTER USER 'class_user'@'%' IDENTIFIED BY '<CLASS_DB_PWD>';
FLUSH PRIVILEGES;
```

> ⚠️ 必须与 `.env.prod` 实际注入的 `CLASS_DB_PWD` 完全一致，否则连接失败。

---

## 5. 数据迁移（旧 prj-mysql → dev-mysql）

旧 `prj-mysql` 容器数据若仍在，先导出再导入；若已丢失，从既有备份恢复。

```bash
# 1) 从旧 prj-mysql 导出（旧库宿主机端口曾为 33060 / 33065，按实际填写 <old-host>）
mysqldump -h <old-host> -P 33065 -u root -p prj_prod > /tmp/prj_prod.sql
mysqldump -h <old-host> -P 33065 -u root -p msg       > /tmp/msg.sql
mysqldump -h <old-host> -P 33065 -u root -p work      > /tmp/work.sql

# 2) 导入 dev-mysql（dev-mysql 宿主机端口 33060）
mysql -h 127.0.0.1 -P 33060 -u root -p prj_prod < /tmp/prj_prod.sql
mysql -h 127.0.0.1 -P 33060 -u root -p msg       < /tmp/msg.sql
mysql -h 127.0.0.1 -P 33060 -u root -p work      < /tmp/work.sql
```

> 注：跨容器网络也可直接用服务名 `dev-mysql:3306`，但 `mysqldump`/`mysql` 在宿主机执行时
> 走宿主机映射端口 33060 即可。

---

## 6. 回滚要点

- `prj-mysql` 的 compose 定义仍存于 git 历史，可恢复查看：

```bash
git show HEAD:docker-compose.prod.yml
```

- 迁移前务必先对 dev-mysql 做全量备份，便于回滚：

```bash
mysqldump -h 127.0.0.1 -P 33060 -u root -p --all-databases > /tmp/dev_mysql_full_$(date +%F).sql
```

- 若需恢复独立 `prj-mysql`，从 `git show HEAD:docker-compose.prod.yml` 取回服务块，
  并将本手册第 1–5 节的改动反向执行（服务名/网络名恢复为 `prj-mysql`/`prj-network`）。

---

## 7. 校验清单

- [ ] dev-mysql 运行：`docker ps --filter name=dev-mysql`
- [ ] 三库存在：`SHOW DATABASES;` 含 `prj_prod` / `msg` / `work`
- [ ] `class_user`@'%'（或 `prj_user`）账号与密码已对齐 `<CLASS_DB_PWD>`
- [ ] 授权生效，后端 `SPRING_DATASOURCE_URL` 指向 `dev-mysql:3306`
- [ ] prj-php 经 `dev-network` 可达 dev-mysql（depends_on `service_started`）
