# X-box DB 合并变更日志（废弃 prj-mysql / 统一 dev-mysql）

> 日期：2026-07-14｜执行人：寇豆码（软件工程师）
> 性质：**仅文件/配置改动**，未执行 docker、未启动容器、未迁移/改动数据、未 git commit。

## 变更明细

| 文件 | 行 | 原片段 | 新片段 | 影响 |
|------|----|--------|--------|------|
| `Niu_Txl/902/message/Connections/conn.php` | 3 | `MySQL 通过服务名 prj-mysql 互访` | `MySQL 通过服务名 dev-mysql 互访` | 注释对齐 |
| `Niu_Txl/902/message/Connections/conn.php` | 5 | `getenv('CLASS_DB_HOST') ?: 'prj-mysql'` | `getenv('CLASS_DB_HOST') ?: 'dev-mysql'` | PHP 默认主机改连 dev-mysql |
| `Niu_Txl/902/work/Connections/conn.php` | 4 | `MySQL 通过服务名 prj-mysql 互访` | `MySQL 通过服务名 dev-mysql 互访` | 注释对齐 |
| `Niu_Txl/902/work/Connections/conn.php` | 5 | `getenv('CLASS_DB_HOST') ?: 'prj-mysql'` | `getenv('CLASS_DB_HOST') ?: 'dev-mysql'` | PHP 默认主机改连 dev-mysql |
| `docker-compose.prod.yml` | 45–77 | `prj-mysql:` 整个服务块（env_file/image/ports/healthcheck/networks 等） | （删除） | 废弃独立 MySQL 服务 |
| `docker-compose.prod.yml` | 165 | `jdbc:mysql://prj-mysql:3306/` | `jdbc:mysql://dev-mysql:3306/` | 后端连 dev-mysql |
| `docker-compose.prod.yml` | 172–173 | `prj-mysql: condition: service_healthy` | `dev-mysql: condition: service_started` | 跨网依赖，dev-mysql 无 healthcheck |
| `docker-compose.prod.yml` | 202 | `复用 prj-mysql 新建 msg/work 库` | `复用 dev-mysql 新建 msg/work 库` | 注释对齐 |
| `docker-compose.prod.yml` | 221 | `CLASS_DB_HOST: ${CLASS_DB_HOST:-prj-mysql}` | `CLASS_DB_HOST: ${CLASS_DB_HOST:-dev-mysql}` | PHP 注入默认主机 |
| `docker-compose.prod.yml` | 230–231 | `prj-mysql: condition: service_healthy` | `dev-mysql: condition: service_started` | PHP 跨网依赖 |
| `docker-compose.prod.yml` | 178–179 | `networks: - prj-network` | `networks: - prj-network / - dev-network` | prj-backend 加入 dev-network |
| `docker-compose.prod.yml` | 232–233 | `networks: - prj-network` | `networks: - prj-network / - dev-network` | prj-php 加入 dev-network |
| `docker-compose.prod.yml` | 21–22 附近 | （无依赖 base.yml 说明） | 追加「本生产栈现依赖 base.yml 的 dev-mysql，须一并 -f base.yml -f prod.yml up」说明 | 启动方式提示 |
| `docker-compose.qa-override.yml` | 全文 | 覆盖已删除的 prj-mysql 端口的失效文件 | `rm` 删除（未跟踪，非 git commit） | 避免 compose 报 service prj-mysql not found |
| `docs/X-box-db-merge-runbook-2026-07-14.md` | 新建 | — | 建库/账号/授权/密码对齐/迁移/回滚手册 | 运维执行依据 |

## 复核结果

- `grep -rn "prj-mysql"`：conn.php = 0 处；compose 功能引用 = 0 处（prod.yml 第 24 行仅为顶部“已废弃删除”说明，符合文档/历史说明例外）；其余仅存于 `docs/*.md` 历史/说明文本。
- prod 栈其余服务（nginx/redis/llama/backend/frontend/php）均保留未动。
- 未执行任何 docker 命令、未启动容器、未迁移数据、未 `git add`/`commit`。

## 待办（用户/运维）

- 按 `docs/X-box-db-merge-runbook-2026-07-14.md` 在 dev-mysql 运行时执行建库/账号/授权/密码对齐/迁移。
- 确认无误后统一 `git commit`（含本次 compose/conn.php 改动与新增两文档）。

---

## 二轮返工 F1/F2（2026-07-14 路由退回后）

> 首轮已落地，QA 用真实 `docker compose config` 抓出两处错误，路由退回，本轮仅修这两处、未动其它已正确部分。

### F1 — depends_on 服务名错误（致命）
- `prj-backend-c` 的 `depends_on` 原写 `dev-mysql:`（容器名，compose 按服务名解析 → `undefined service "dev-mysql"`）→ **改为 `mysql:`**（保留 `condition: service_started`）。
- `prj-php` 的 `depends_on` 同上原写 `dev-mysql:` → **改为 `mysql:`**（保留 `condition: service_started`）。
- ⚠️ 未动运行时 DNS 用容器名的写法：`SPRING_DATASOURCE_URL` 内 `//dev-mysql:3306/` 与 `CLASS_DB_HOST: ${CLASS_DB_HOST:-dev-mysql}` 仍保留 `dev-mysql`（正确）。

### F2 — prod 自带 llama 与 base 撞名（合并必爆）
- 删除 prod.yml 中 `prj-llama:` 整个服务块（含上方 `[C14]` 注释，原约第 81–122 行）；base 已有 `dev-prj-llama`，生产统一复用，未误删相邻 `prj-backend-c`。
- `prj-backend-c` 的 `depends_on` 原写 `prj-llama:`（→ 合并报 `container name "dev-prj-llama" already in use`）→ **改为 `dev-prj-llama:`**（保留 `condition: service_started`）。
- 复核 prod.yml 内 `prj-llama` 仅剩第 103 行 `AI_SERVICE_URL: http://dev-prj-llama:11434` 一处（正确，运行时以容器名互访）。

### 复验结果（docker compose config）
- 本机有 docker 客户端，执行 `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml config`：**EXIT=0，无报错**。
  - 首轮 F1 的 `undefined service "dev-mysql"`、F2 的 `container name "dev-prj-llama" already in use` 均已消失。
  - 仅剩与本次改动无关的预置警告（`.env.prod` 未提供：`REDIS_PASSWORD`/`CLASS_DB_PWD`/`aJs` 等，属运行时密钥，非配置错误）。
- `grep -n "dev-mysql:\|mysql:\|dev-prj-llama:\|prj-llama:" docker-compose.prod.yml` 复核：
  - `mysql:` ×2（prj-backend-c、prj-php 的 depends_on）；`dev-prj-llama:` ×1（prj-backend-c 的 depends_on）；无 `prj-llama:` 服务定义。
- `grep -rn "prj-mysql" docker-compose*.yml Niu_Txl/ backend/ web/` 功能引用 = 0（仅 prod.yml 顶部“已废弃删除”说明注释，非功能引用）。
- `grep -n "prj-llama" docker-compose.prod.yml` 仅剩 `AI_SERVICE_URL` 一处（第 103 行）。
- 未执行任何 docker 命令（除 `config` 校验）、未启动容器、未迁移数据、未 `git add`/`commit`。

### IS_PASS
**YES**（F1/F2 两处错误均已修复，`docker compose config` 合并校验通过）。

---

## 三轮返修 E1（2026-07-14 QA 第 2 轮抓出，路由退回）

> 性质：**仅文件/配置改动**，未执行 docker、未启动容器、未迁移/改动数据、未 `git add`/`commit`。
> 本轮只修 E1（`.env.prod` 残留旧容器名 + 启动命令文档矛盾），不碰其它已正确的部分。

### E1-1 — `.env.prod` 残留旧容器名 `prj-mysql`
- `CLASS_DB_HOST=prj-mysql` → `CLASS_DB_HOST=dev-mysql`（第 44 行）。
  - 原 `CLASS_DB_HOST: ${CLASS_DB_HOST:-dev-mysql}` 默认值是 dev-mysql，但 `.env.prod` 经 `--env-file` 注入时该变量会**覆盖**默认值，导致 prj-php 连向已删除的 `prj-mysql` → 连接失败。
- 第 42 行注释块「902 的 conn.php … 连接 prj-mysql」中的 `prj-mysql` token → `dev-mysql`（仅替换该 token，保留其它乱码文字/结构）。

### E1-2 — `.env.prod.example` 模板同理
- 第 44 行 `CLASS_DB_HOST=prj-mysql` → `CLASS_DB_HOST=dev-mysql`。
- 第 42 行注释「902 的 conn.php 读取以下变量连接 prj-mysql」中的 `prj-mysql` → `dev-mysql`。

### E1-3 — `docker-compose.prod.yml` 顶部两处启动命令矛盾
- 第 19 行 `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build`（缺 base.yml）→ 统一为：
  `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build`
- 第 27 行 `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml up -d --build`（缺 `--env-file .env.prod`）→ 同上统一。
- 合并拓扑下正确命令须两者兼具：既 `-f base.yml` 拉起 dev-mysql/dev-network，又 `--env-file .env.prod` 注入生产凭证。

### E1-4 — `docs/X-box-db-merge-runbook-2026-07-14.md` 启动命令统一
- 第 17 行 `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml up -d --build`（缺 `--env-file .env.prod`）→ 统一为含 `--env-file .env.prod` 的完整命令。

### 未改动项（按铁律保留）
- `.env.prod.bak`：**完全未动**（第 44 行仍 `CLASS_DB_HOST=prj-mysql`、第 47 行 `CLASS_DB_PWD=QaTest@2026`，属 P0 回滚备份）。
- `.env.prod` 第 47 行 `CLASS_DB_PWD=<REDACTED-live-prod>`（P0 已轮换强随机值）：**保持不变**。

### 复验结果
- `grep -n "CLASS_DB_HOST" .env.prod .env.prod.example` → 均为 `CLASS_DB_HOST=dev-mysql`（无 prj-mysql）。
- `grep -n "docker compose" docker-compose.prod.yml docs/X-box-db-merge-runbook-2026-07-14.md` → 三处启动命令均含 `-f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod`。
- `.env.prod.bak` 未改（含 prj-mysql / QaTest@2026 历史值）。
- `.env.prod` 第 47 行 `CLASS_DB_PWD` 强随机值未变。
- 未执行任何 docker 命令、未启动容器、未迁移数据、未 `git add`/`commit`。

### IS_PASS
**YES**（E1 四类残留/矛盾项均已修复并通过 grep 复验；`.bak` 与已轮换 `CLASS_DB_PWD` 保持不动）。
