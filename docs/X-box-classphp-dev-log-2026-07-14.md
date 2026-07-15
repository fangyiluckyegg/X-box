# X-box：Niu_Txl(PHP) 纳入开发（dev）体系 交付记录

- 日期：2026-07-14
- 负责人：交付总监齐活林（主理人）→ 工程师寇豆码 → QA 严过关
- 工作流：快速模式（单文件新建，1 compose + 1 env 改动）
- 结论：IS_PASS = YES，QA 路由 NoOne，docker compose config 实跑 EXIT=0

---

## 1. 背景与决策

用户指出：X-box 仍在**开发阶段**，起 PHP（Niu_Txl / 902/message）不该用 prod 类文件，应当走 dev 体系。

经主理人核实的项目事实：
- 开发环境有两套文件，但**均无 PHP 服务定义**：
  - `docker-compose.business-prj.yml`（注释自述"开发/内网编排，dev profile"，文件名却无 `.dev` 后缀，易误导）
  - `docker-compose.business-prj.dev.yml`（最精简 dev：仅 frontend + backend-c）
- 真正的生产栈是 `docker-compose.prod.yml`。
- **PHP（prj-php）此前唯一的编排入口只在 `docker-compose.prod.yml`（第 140–170 行）**。即开发阶段要跑 902/message，只能借用 prod.yml —— 这正是此前截图 `127.0.0.1:1181` 走 prod 配置、且报 `prj-mysql` 的根因（DB 合并前旧状态）。

用户拍板采用**方案 B**：新建 dev 专属 PHP 编排，开发阶段起 PHP 完全不依赖 prod 文件。

---

## 2. 交付物

| 操作 | 文件 | 内容 |
|------|------|------|
| 新建 | `docker-compose.classphp.dev.yml` | dev 体系 PHP 服务 `prj-php`（容器名 `dev-prj-php`），build `./Niu_Txl` + `Dockerfile.classphp`，`env_file: .env.dev` 注入 `CLASS_DB_*`，`environment: CLASS_DB_HOST: dev-mysql`，端口 `127.0.0.1:1181:80`，`depends_on: mysql (service_started)`，网络 `dev-network`，无 `platform` 字段，`restart: "no"` |
| 修改 | `.env.dev` | 末尾追加 `CLASS_DB_USER=class_user`、`CLASS_DB_PWD=ClassDev@2026Xbox!`（未覆盖任何原有变量） |

**关键设计对齐 dev 体系**（区别于 prod）：
- 容器名 `dev-` 前缀（dev-prj-php）
- 无 `platform: linux/arm64`（business-prj.dev.yml 也未用）
- `restart: "no"`
- 复用 base 的 `dev-mysql`（服务名 `mysql`）与 `dev-network`
- 密码用 dev 专用值，不混入 prod 强随机口令

---

## 3. 验证结果（实跑）

`docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml -f docker-compose.classphp.dev.yml --env-file .env.dev config` → **EXIT=0**

合并渲染确认：
- `prj-php` → `container_name: dev-prj-php`
- `CLASS_DB_HOST: dev-mysql`、`CLASS_DB_USER: class_user`、`CLASS_DB_PWD: ClassDev@2026Xbox!` 已注入容器（USER/PWD 经 env_file）
- `dev-network` 在 base / business-prj.dev / 本文件三处同名合并为 `x-box_dev-network`，所有服务正确挂接

QA 独立复核 7 项全 PASS：compose 读审、无 prod 依赖（mtime 证明 prod 文件早于 dev 交付未被触、内容零功能引用）、`.env.dev` 变量完好、config 实跑、构建目标有效、nginx 复用无断链、conn.php 读取一致。

---

## 4. 开发阶段启动 + 建库（运维动作，给用户执行）

```bash
# ① 启动全部 dev 服务（含 PHP），彻底不碰 prod 文件
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml -f docker-compose.classphp.dev.yml --env-file .env.dev up -d

# ② 在 dev-mysql 建 PHP 需要的库与账号
#    root 口令用 .env.dev 的 MYSQL_ROOT_PASSWORD=Root@Dev123456
#    class_user 密码用 .env.dev 新增的 CLASS_DB_PWD=ClassDev@2026Xbox!
docker exec -i dev-mysql mysql -uroot -pRoot@Dev123456 <<'SQL'
CREATE DATABASE IF NOT EXISTS msg  CHARACTER SET utf8;
CREATE DATABASE IF NOT EXISTS work CHARACTER SET utf8;
CREATE USER IF NOT EXISTS 'class_user'@'%' IDENTIFIED BY 'ClassDev@2026Xbox!';
GRANT ALL PRIVILEGES ON msg.*  TO 'class_user'@'%';
GRANT ALL PRIVILEGES ON work.* TO 'class_user'@'%';
FLUSH PRIVILEGES;
SQL

# ③ 验证 PHP 连库（容器已起后）
docker exec dev-prj-php php -r "var_dump(mysqli_connect('dev-mysql','class_user','ClassDev@2026Xbox!','msg'));"

# ④ 刷新 http://127.0.0.1:1181/902/message/index.php 应正常
```

> 若此前 dev-mysql 已有旧数据，先 `mysqldump` 备份再建库；新库为空时先验证连通性即可。

---

## 5. 已知备查（非阻塞）

1. **建库为运维动作**：dev-mysql 默认仅有 `prj_dev` 库 + `prj_user`，PHP 需要的 `msg`/`work` 库与 `class_user` 账号需按第 4 节手动建立（不在本次代码交付范围）。
2. **conn.php 注释小瑕疵**：`Niu_Txl/902/message/Connections/conn.php` 第 3–4 行注释写「见 docker-compose.prod.yml」，dev 语境下不准确，建议改为指向 `docker-compose.classphp.dev.yml`。仅注释问题，不影响功能，按需处理。
3. **未 git 提交**：Niu_Txl 未跟踪、compose/env 改动按用户习惯后续统一提交。
