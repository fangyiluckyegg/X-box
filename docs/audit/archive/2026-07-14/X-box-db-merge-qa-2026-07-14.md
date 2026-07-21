# X-box DB 合并 QA 复核报告（prj-mysql → dev-mysql）

> 复核人：严过关（QA 工程师）｜轮次：**第 2 轮（最后一轮）**
> 日期：2026-07-14｜方式：独立取证（Read 实际文件 + 实际运行命令）
> 对象：工程师二轮返工 F1/F2（声称已修且 `docker compose config` EXIT=0）

---

## 一、逐项复核（声明 → 实际取证 → PASS/FAIL）

### S1 — F1：depends_on 服务名 + 运行时容器名保留
- **工程师声明**：prj-backend-c 与 prj-php 的 `depends_on` 下 `dev-mysql:` 已改 `mysql:`（`service_started` 保留）；URL（第141行 `//dev-mysql:3306/`）与 `CLASS_DB_HOST`（第198行 `dev-mysql`）容器名写法保留。
- **实际取证**：
  - `docker-compose.prod.yml:107-108` → `mysql:` / `condition: service_started`（prj-backend-c）✓
  - `docker-compose.prod.yml:166-167` → `mysql:` / `condition: service_started`（prj-php）✓
  - `docker-compose.prod.yml:100` → `SPRING_DATASOURCE_URL: jdbc:mysql://dev-mysql:3306/...`（容器名 dev-mysql 保留）✓
  - `docker-compose.prod.yml:157` → `CLASS_DB_HOST: ${CLASS_DB_HOST:-dev-mysql}`（容器名 dev-mysql 保留）✓
  - ⚠️ 偏差：工程师所述行号 141/198 与实际 **100/157 不符**（疑似对照旧版快照）；内容正确，仅行号漂移。
- **判定：PASS**

### S2 — F2：删除 prod 自带 prj-llama，统一 dev-prj-llama
- **工程师声明**：prod `prj-llama:` 整块（含 [C14] 注释）已删；prj-backend-c depends_on `prj-llama:` → `dev-prj-llama:`；全文件 `prj-llama` 仅剩 `AI_SERVICE_URL` 一处。
- **实际取证**：
  - `grep -nE "^  prj-llama:" docker-compose.prod.yml` → 无匹配（exit 1），即**无独立 `prj-llama:` 服务定义块** ✓
  - `docker compose config` 解析后的 services 列表：**无 `prj-llama`**（仅有 `dev-prj-llama` / `mysql` 等）✓
  - `docker-compose.prod.yml:111` → `dev-prj-llama:` / `condition: service_started`（prj-backend-c depends_on）✓
  - `docker-compose.prod.yml:105` → `AI_SERVICE_URL: http://dev-prj-llama:11434`，为文件中唯一 `prj-llama` 字面（位于 `dev-prj-llama` 子串内）✓
  - prj-backend-c 服务块完整存在（`docker-compose.prod.yml:83-115`），未被误删 ✓
  - ⚠️ 偏差：工程师所述"第103行"实际为 **105**。
- **判定：PASS**

### S3 — docker compose config 合并校验
- **实际取证**：本机有 docker 客户端。执行
  `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml config`
  → **EXIT=0**；无 `undefined service` / `container name already in use` 报错。仅 `REDIS_PASSWORD`/`CLASS_DB_PWD`/`aJs` 未提供警告（属运行时密钥，非配置错误）。
- **判定：PASS**

### S4 — 功能引用 `prj-mysql` = 0（任务给定范围）
- **实际取证**：`grep -rn "prj-mysql" docker-compose*.yml Niu_Txl/ backend/ web/`
  → 仅 `docker-compose.prod.yml:24` 注释「原独立 prj-mysql 已废弃删除」，无功能引用。
- **判定：PASS（按任务给定范围）**

### S5 — conn.php 默认主机为 dev-mysql
- **实际取证**：
  - `Niu_Txl/902/message/Connections/conn.php:5` → `$hostname_conn = getenv('CLASS_DB_HOST') ?: 'dev-mysql';` ✓
  - `Niu_Txl/902/work/Connections/conn.php:5` → 同上 ✓
- **判定：PASS**

### S6 — qa-override 已删
- **实际取证**：`ls docker-compose.qa-override.yml` → `No such file or directory`。
- **判定：PASS**

### S7 — runbook / log 文档完整
- **实际取证**：
  - `docs/audit/archive/2026-07-14/docs/X-box-db-merge-runbook-2026-07-14.md` 含建库(§1)/账号(§2)/改密(§4)/GRANT(§3)/迁移(§5)/回滚(§6) ✓
  - `docs/audit/archive/2026-07-14/docs/X-box-db-merge-log-2026-07-14.md` 含「二轮返工 F1/F2」小节（§39–64）✓
- **判定：PASS**

---

## 二、独立额外取证（超出任务清单，但为发布阻塞项）

### E1 — `.env.prod` 残留旧容器名 `CLASS_DB_HOST=prj-mysql`（FAIL）
- **取证**：
  - `grep -rn "CLASS_DB_HOST" .env*` →
    - `.env.prod:44` → `CLASS_DB_HOST=prj-mysql`
    - `.env.prod.example:44`、`.env.prod.bak:44` 同值
  - `docker-compose.prod.yml:19` 注释的启动命令为
    `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build`
- **影响分析**：
  1. prj-php 的 `environment: CLASS_DB_HOST: ${CLASS_DB_HOST:-dev-mysql}` 在 `--env-file .env.prod` 下会被插值为 **`prj-mysql`**；而 `conn.php:5` 的 `?: 'dev-mysql'` 仅在变量**为空**时回退，此时变量非空 → PHP 连向**不存在的 `prj-mysql` 主机** → 502/连接失败。
  2. 这与「统一复用 dev-mysql」的合并决策直接冲突。S4/S5 的 PASS 仅因 grep 范围未含 `.env*`，`.env.prod` 是实际部署注入环境，属功能性 `prj-mysql` 引用。
- **文档不自洽（连带）**：
  - `docker-compose.prod.yml:19` README 命令：用 `--env-file .env.prod` 但**缺 base.yml** → dev-mysql 未定义。
  - `docs/...runbook:17` 命令：`docker compose -f base.yml -f prod.yml up`（**缺 `--env-file`**）→ `CLASS_DB_PWD`/`REDIS_PASSWORD` 空 → PHP/Redis 连接失败（本次 config 已现 `CLASS_DB_PWD variable is not set` 警告）。
  - **正确命令应为**：`docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build`，且须先将 `.env.prod` 的 `CLASS_DB_HOST` 改为 `dev-mysql`。
- **判定：FAIL（发布阻塞）**

---

## 三、第 2 轮复测结论

F1（depends_on 服务名）、F2（prj-llama 撞名）、`docker compose config` EXIT=0 三项**经真实取证确认通过**，非橡皮图章——首轮两处致命错误确已修复。

但独立取证发现：`.env.prod`（及 .example/.bak）第 44 行仍残留旧容器名 `CLASS_DB_HOST=prj-mysql`，并与启动命令文档（README 与 runbook 互斥）不自洽。按文档任一部署路径，PHP 均无法连库（或连错主机、或密码为空）。**合并未达可发布状态。**

---

## 四、路由判定

**Engineer**

- 必须修：`D:\crh123dexiaohao\X-box\.env.prod:44`（及 `.env.prod.example:44`、`.env.prod.bak:44`）`CLASS_DB_HOST=prj-mysql` → `dev-mysql`。
- 必须修：统一启动命令——README(`docker-compose.prod.yml:19`) 与 runbook(§0/§17) 应一致为
  `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build`。
- 复测 S1–S7 已 PASS，无需重做；仅需针对 E1 返修后复验 `.env.prod` 与一次 `docker compose config`。

> 附：本环境未跑实栈；网络/凭证/数据迁移仍须按 runbook 在部署环境执行；合并前务必先 `mysqldump --all-databases` 备份 dev-mysql。

---

**一句话结论**：F1/F2 与 `docker compose config` EXIT=0 复测均 PASS，但 `.env.prod:44` 仍残留 `CLASS_DB_HOST=prj-mysql` 且与启动命令文档冲突，生产 PHP 将连库失败，故路由 Engineer 返修。

---

## 五、第 3 轮复测（E1，最终轮）——独立取证

> 复核人：严过关（QA）｜轮次：**第 3 轮（最终轮）**｜日期：2026-07-14
> 对象：工程师声称 E1 已修（`.env.prod`/`.example` 的 `CLASS_DB_HOST` 改为 `dev-mysql`、启动命令三处统一、`.bak` 未动）
> 方式：独立取证（Read 实际文件 + 本机真实运行 `docker compose config`）

### T1 — `CLASS_DB_HOST` 值（`.env.prod` / `.env.prod.example`）
- 取证：`grep -n "CLASS_DB_HOST" .env.prod .env.prod.example`
  - `.env.prod:44` → `CLASS_DB_HOST=dev-mysql` ✓
  - `.env.prod.example:44` → `CLASS_DB_HOST=dev-mysql` ✓
- 判定：**PASS**（无 prj-mysql）

### T2 — `.env.prod.bak` 保留（历史回滚，未误改）
- 取证：`grep -c "prj-mysql" .env.prod.bak` → `2`（>0，保留旧值）；mtime `.env.prod.bak`=20:21 < `.env.prod`=22:27，证实 `.bak` 未被本轮改动触碰。
- 判定：**PASS**（.bak 未动，符合"历史回滚保留"约定）

### T3 — 启动命令三处统一且完整
- 取证：`grep -n "docker compose" docker-compose.prod.yml docs/audit/archive/2026-07-14/docs/X-box-db-merge-runbook-2026-07-14.md`（Read 确认 19/27 行）
  - `docker-compose.prod.yml:19` → `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build` ✓
  - `docker-compose.prod.yml:27` → 同上完整命令 ✓
  - `docs/audit/archive/2026-07-14/docs/X-box-db-merge-runbook-2026-07-14.md:17` → 同上完整命令 ✓
- 判定：**PASS**（三处一致、完整含 base + `--env-file`）

### T4 — 全量回归 `prj-mysql` 功能引用 = 0
- 取证：`grep -rn "prj-mysql" docker-compose*.yml Niu_Txl/ backend/ web/ .env.prod .env.prod.example`
  - 仅 `docker-compose.prod.yml:24` 历史说明注释「原独立 prj-mysql 已废弃删除」，无功能引用（无服务名/主机/depends_on 引用）。
- 判定：**PASS**（按任务给定范围；24 行为允许保留的历史说明）

### T5 — 无独立 `prj-llama` 服务
- 取证：`grep -n "prj-llama:" docker-compose.prod.yml` → 命中 105/111，均为 `dev-prj-llama` 子串：
  - `:105` `AI_SERVICE_URL: http://dev-prj-llama:11434`（任务允许）
  - `:111` `dev-prj-llama:`（depends_on 引用，非服务定义键）
  - 无独立 `prj-llama:` 服务键。
- 判定：**PASS**（仅 `dev-prj-llama`，符合预期）

### T6 — depends_on 模式 `mysql:`×2 + `dev-prj-llama:`×1
- 取证（ripgrep 精确匹配 `^\s*(mysql:|dev-prj-llama:|prj-llama:)`）：
  - `:107` `mysql:`（prj-backend-c）
  - `:111` `dev-prj-llama:`（prj-backend-c）
  - `:166` `mysql:`（prj-php）
- 判定：**PASS**（与预期完全一致；首轮 `\|` 转义致空匹配为工具假阴性，已用 ripgrep 复核确认）

### T7 — `docker compose config` 合并校验
- 取证：本机 docker v29.6.1 / Compose v5.2.0。执行
  `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config`
  → **EXIT=0**；解析 services 含 `mysql`(container_name `dev-mysql`)、`dev-prj-llama`，无 `undefined service` / `container name already in use`，无 `prj-mysql` 相关报错。仅 `aJs variable is not set` 警告（无关 DB 合并，属运行期变量）。
- 判定：**PASS**

### T8 — conn.php 默认主机 `dev-mysql`
- 取证：Read 两文件第 5 行
  - `Niu_Txl/902/message/Connections/conn.php:5` → `$hostname_conn = getenv('CLASS_DB_HOST') ?: 'dev-mysql';` ✓
  - `Niu_Txl/902/work/Connections/conn.php:5` → 同上 ✓
- 判定：**PASS**

### T9 — qa-override 不存在
- 取证：`ls docker-compose.qa-override.yml` → `No such file or directory`。
- 判定：**PASS**

---

## 六、第 3 轮复测结论

T1–T9 全量 **PASS**：`CLASS_DB_HOST` 已统一为 `dev-mysql`、`.bak` 保留旧值未被误改、启动命令三处一致完整、`docker compose config` EXIT=0 且 `dev-mysql`/`dev-prj-llama` 解析正常、无独立 `prj-llama` 服务、无功能 `prj-mysql` 引用、conn.php 回退主机为 `dev-mysql`、qa-override 已删。前两轮 F1/F2 亦未被本轮改动破坏。**E1 确已修复，非橡皮图章。**

---

## 七、路由判定（最终）

**NoOne** —— 报告成功，可关闭。

> 注：本环境未跑实栈。部署环境仍须按 runbook 执行建库（prj_prod/msg/work）、class_user 账号与授权、密码对齐、数据迁移；合并前务必先 `mysqldump --all-databases` 备份 dev-mysql。

---

**一句话结论**：E1 复测全量 PASS——`.env.prod/.example` 的 `CLASS_DB_HOST` 已为 `dev-mysql`、`.bak` 未误改、启动命令三处统一、`docker compose config` EXIT=0 无 prj-mysql 报错、无独立 `prj-llama` 服务，路由 **NoOne** 关闭。
