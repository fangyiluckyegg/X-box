# X-box PROD 生产环境静态就绪性核查报告

- **核查类型**：PROD 配置静态就绪性核验（纯核查，未改动任何源码/配置）
- **核查人**：QA 工程师 Edward（software-qa-engineer）
- **日期**：2026-07-20
- **项目路径**：`D:\crh123dexiaohao\X-box`（working tree clean，已提交）
- **环境**：Windows / Intel，**无法做真实 ARM64 / Mac 运行时验证**；仅做静态配置就绪性 + 外部依赖识别
- **权威启动命令**：
  ```
  cd D:\crh123dexiaohao\X-box
  docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build
  ```
- **合并拓扑**：base + prod 2 文件合并，共 **7 个服务**

---

## 一、总体结论

| 项目 | 结论 |
|---|---|
| **PROD 配置能否在 Mac 上从零跑通？** | **有条件 YES**（宿主/数据前置，非代码缺陷） |
| 硬阻塞（启动即失败） | **无** |
| 软阻塞（需前置准备） | Ollama 宿主原生（AI 功能）、ARM64 首次构建耗时 |
| 智能路由判定 | **NoOne**（纯核查，未发现源码/配置 Bug） |

> 说明：所有构成「启动失败 / 连不上库」的硬阻塞点均未发现。唯一需要 Mac 部署方落实的是**宿主前置条件**（Ollama）与**数据前置**（全新 `mysql_data` 卷或按 runbook 处理存量卷）。配置本身可达自洽。

---

## 二、逐条核查结果

### 【A】compose 合并合法性（实跑 `docker compose config`）

- **退出码**：`0` ✅
- **解析出的服务数 = 7** ✅（去重后）
- **服务清单与端口映射**：

| 服务 | 来源 | 镜像 | platform | 宿主端口 | 网络 |
|---|---|---|---|---|---|
| nginx-gateway | base | nginx:1.25-alpine | — | `127.0.0.1:80` + `:443` | dev-network |
| dev-mysql | base | mysql:8.0 | — | `127.0.0.1:33060` | dev-network |
| dev-redis | base | redis:7-alpine | — | `127.0.0.1:63790` | dev-network |
| prj-redis | prod | redis:7-alpine | linux/arm64 | **无宿主端口** ✅ | dev-network |
| prj-backend-c | prod | build(arm64) | linux/arm64 | 无（仅内网） | dev-network |
| prj-frontend | prod | build(arm64) | linux/arm64 | `127.0.0.1:8081` | dev-network |
| prj-php | prod | build(arm64) | linux/arm64 | `127.0.0.1:1181` | dev-network |

- **端口零冲突核验**：宿主端口集合 = `{80, 443, 33060, 63790, 8081, 1181}`，**无重复** ✅
  - `prj-redis` 已正确**移除宿主端口映射**（仅内网可达，避免与 `dev-redis:63790` 冲突）✅
  - `prj-backend-c` 无宿主端口（仅经 `dev-network` 内网互通）✅
- **插值 WARNING**：合并命令**未打印任何** `The ... variable is not set` / 插值 WARNING，stderr 为空 ✅

### 【B】prod 构建 Dockerfile 存在性

| Dockerfile | 路径 | 结果 |
|---|---|---|
| backend | `backend/prj-backend-c/Dockerfile.prod` | ✅ 存在 |
| frontend | `web/prj-frontend/Dockerfile.prod` | ✅ 存在 |
| php | `Niu_Txl/Dockerfile.classphp` | ✅ 存在 |

→ 三处 `build.dockerfile` 指向均存在，build 不会因缺失而失败 ✅

### 【C】bind 挂载源目录存在性

| 来源 | 挂载点 | 结果 |
|---|---|---|
| base | `gateway/nginx/conf.d` | ✅（含 `prj.conf` 7992B） |
| base | `gateway/nginx/ssl` | ✅（含 `prj.crt` / `prj.key`） |
| base | `logs/nginx` | ✅ |
| base | `db/mysql_init` | ✅ |
| base | `db/mysql_scripts` | ✅（含 wrapper 脚本） |
| base | `db/redis_data` | ✅ |
| base | `logs/mysql` | ✅ |
| prod(prj-redis) | `db/redis_data` | ✅（与 base 复用同一目录） |
| prod(prj-redis) | `logs/redis` | ✅（与 base 复用同一目录） |
| prod(prj-frontend) | `logs/prj-frontend` | ✅ |
| prod(prj-php) | `Niu_Txl` | ✅ |
| prod(prj-php) | `logs/prj-php` | ✅ |

→ 全部 12 个挂载源存在，容器创建不会因缺失挂载而失败 ✅

> ⚠️ **INFO（非阻塞）**：`dev-redis` 与 `prj-redis` 共享同一 `./db/redis_data:/data` 与 `./logs/redis`。两 Redis 实例会各自写同一 `dump.rdb`，属设计层面的轻微耦合，但不影响启动（两者口令经 `--env-file .env.prod` 现已一致，均为 `<REDACTED-live-prod>`）。

### 【D】`.env.prod` 完整性与插值安全（核心风险点）

**1) 必需变量完整性**（在 `.env.prod` 中均已定义）：

| 变量 | 引用位置 | 是否无默认值 `${VAR}` | 在 .env.prod 定义 |
|---|---|---|---|
| `REDIS_PASSWORD` | prj-redis command+healthcheck、prj-backend-c `REDIS_PASSWORD` | 是（无默认） | ✅ `<REDACTED-live-prod>` |
| `CLASS_DB_PWD` | prj-php `CLASS_DB_PWD` | 是（无默认） | ✅ `<REDACTED-live-prod>` |
| `SPRING_DATASOURCE_PASSWORD` / `JWT_SECRET` / `DRUID_*` / `AI_API_TOKEN` / `MYSQL_DATABASE` 等 | prj-backend-c `env_file: .env.prod` 注入 | — | ✅ 均存在 |
| `CLASS_DB_HOST` / `CLASS_DB_USER` | prj-php，有默认 `dev-mysql` / `class_user` | 有默认 | ✅ 缺亦可（已定义） |
| `MYSQL_DATABASE` | prj-backend-c，有默认 `prj_dev` | 有默认 | ✅ 定义 `prj_dev` |

→ 所有无默认值变量均在 `.env.prod` 中定义，**不会触发 "variable is not set"** ✅

**2) 逐字符 `$` 扫描结果（`.env.prod` 共 3 行含 `$`）**：

| 行 | 变量 | 原文片段 | 判定 | 结论 |
|---|---|---|---|---|
| 12 | `MYSQL_ROOT_PASSWORD` | `<REDACTED-live-prod>!(n;_hrlRCl4=:*zh`**`$$aJs`** | `$$` 为转义（历史修复就位：`$$aJs` 而非 `$aJs`） | ✅ 安全（字面 `$aJs`） |
| 29 | `PRJ_DB_PWD` | `<REDACTED-live-prod>`**`$<`**`<REDACTED-live-prod>` | `$` 后为 `<`（非变量名首字符 a-zA-Z_）→ 字面量 | ✅ 安全 |
| 31 | `SPRING_DATASOURCE_PASSWORD` | `<REDACTED-live-prod>`**`$<`**`<REDACTED-live-prod>` | 同上，`$<` 为字面量 | ✅ 安全 |

- **未发现任何「未被 `$$` 转义 且 `$` 后紧跟 a-zA-Z_」的意外插值** ✅
- 凭证口令 `<REDACTED-live-prod>` 中 `$` 仅出现在 `$<`，`$` 后是 `<`/`!`（均非变量名首字符），compose 视为字面量 → **安全**。
  - 注：任务描述中提及的 `$!` 在真实字符串中并不存在，实际为 `$<` 后接 `!`；不影响安全结论。
- **插值安全结论：PASS** ✅（所有 `$` 均为转义 `$$` 或字面量 `$<`，无清空/替换风险）

> 技术附注：合并 `config` 输出中 `SPRING_DATASOURCE_PASSWORD` 显示为 `$$<!1=...` 是 compose `config` 渲染器对**字面 `$` 的转义展示**（`$$` 仅用于让 dump 出的 YAML 不被二次插值），容器内实际注入值仍为**单 `$`** 的 `<REDACTED-live-prod>`（env_file 注入为字面量，不二次插值），与 `.env.dev` 一致，凭证契约成立。

### 【E】凭证契约（prod 在 Mac 实跑时能否连库）

**prj_user（后端连 dev-mysql）—— PASS ✅**

| 文件 | `SPRING_DATASOURCE_PASSWORD` |
|---|---|
| `.env.prod` | `<REDACTED-live-prod>` |
| `.env.dev` | `<REDACTED-live-prod>` |
| **是否相等** | **是** ✅ |

- 运行时：`dev-mysql` 的 `ensure_app_user()`（wrapper）读取容器 `SPRING_DATASOURCE_PASSWORD` = `.env.dev` 值（base 未用 `environment:` 覆盖该变量）；`prj-backend-c` 经 `env_file: .env.prod` 读取同值 → 二者一致，后端可连 ✅

**class_user（prj-php 连 dev-mysql）—— 运行时 PASS ✅（但存在隐性耦合，见下）**

| 文件 | `CLASS_DB_PWD` |
|---|---|
| `.env.prod` | `<REDACTED-live-prod>` |
| `.env.dev` | `ClassDev2026Xbox` |
| **两文件是否相等** | **否** ❌（字面差异） |

- **但运行时一致**：base 的 `environment: CLASS_DB_PWD: ${CLASS_DB_PWD}` 会以 `--env-file .env.prod` 解析，**覆盖** `env_file: .env.dev` 的值。合并 `config` 已证实 `dev-mysql` 容器的 `CLASS_DB_PWD = <REDACTED-live-prod>`（与 `.env.prod` 一致）。`prj-php` 经 `env_file: .env.prod` 读取同值 → `ensure_class_user()` 与 `prj-php` 口令一致，**可连** ✅
- **凭证契约结论：PASS**（两账号运行时均自洽）。

> ⚠️ **软风险 / 文档缺口（建议项，非缺陷）**：
> 1. `.env.prod` 与 `.env.dev` 的 `CLASS_DB_PWD` 字面不一致，但靠 base `environment: CLASS_DB_PWD: ${CLASS_DB_PWD}` 从 `--env-file` 重新解析“隐式对齐”。该机制未被 `docker-compose.prod.yml` 头部的【凭证契约】注释提及（该注释只强调了 `SPRING_DATASOURCE_PASSWORD`）。若后续有人**仅按注释对齐 `SPRING_DATASOURCE_PASSWORD`** 而改动 base 的覆盖行，或**拆分启动**（base 用 `.env.dev`、prod 用 `.env.prod`），`class_user` 将失配。
> 2. 建议：要么在 prod 头部注释补一句 `CLASS_DB_PWD` 同样由 `--env-file` 统一解析、无需手动对齐 `.env.dev`；要么直接将两文件 `CLASS_DB_PWD` 设为相同值以消除歧义。**当前文档化命令下不触发故障。**

**prj-redis 自洽性—— PASS ✅**
- `prj-redis` 的 command/healthcheck 用 `${REDIS_PASSWORD}`，`prj-backend-c` 的 `REDIS_PASSWORD=${REDIS_PASSWORD}`，同源（均来自 `.env.prod`）→ prod 内部自洽 ✅
- 注：与 base `dev-redis` 口令不同是**有意为之**的评论已过时——在 `--env-file .env.prod` 下两者现已同为 `<REDACTED-live-prod>`（base 的 `${REDIS_PASSWORD:-default}` 亦解析自 `.env.prod`）。属注释陈旧，非缺陷。

### 【F】platform / ARM64 就绪

- **4 个 prod 构建服务均带 `platform: linux/arm64`** ✅（合并 `config` 已确认：prj-redis / prj-backend-c / prj-frontend / prj-php）
- base 服务（nginx-gateway / dev-mysql / dev-redis）无显式 platform，但均使用**多架构镜像**（nginx:1.25-alpine、mysql:8.0、redis:7-alpine 均有 arm64 变体）→ Mac M2 可拉取 ✅（仅观察，base 无需显式 platform）
- **3 个 prod Dockerfile 均无 x86-only 指令**：
  - `backend/prj-backend-c/Dockerfile.prod`：基础镜像 `eclipse-temurin:17-jdk-alpine` / `:17-jre-alpine`（多架构）→ 无硬编码 amd64 URL ✅
  - `web/prj-frontend/Dockerfile.prod`：基础镜像 `node:18-alpine` / `nginx:1.25-alpine`（多架构）→ 无硬编码 amd64 URL ✅
  - `Niu_Txl/Dockerfile.classphp`：基础镜像 `php:8.2-apache`（官方镜像含 arm64 变体）→ 无硬编码 amd64 URL ✅
  - 全局 grep `amd64|x86_64|arch=amd64` → 命中 0 ✅

### 【G】外部依赖

- **Ollama（宿主原生）**：`prj-backend-c` 的 `AI_SERVICE_URL=http://host.docker.internal:11434`，已配 `extra_hosts: host.docker.internal:host-gateway`（Mac/OrbStack 可解析兜底）。**前置条件（非阻塞启动）**：宿主需先启动 Ollama 并 `ollama pull bge-m3`；否则容器正常起、但 AI 向量功能不可用。
- **Docker 镜像仓库**：首次 `up --build` 需从 Docker Hub / 阿里云镜像拉取 `eclipse-temurin`、`node`、`php:8.2-apache` 等 + 多阶段 `mvn package` / `npm run build`，**ARM64 首次构建耗时较长**（QEMU 仿真），属预期。

### 【H】MySQL 账号创建来源（prod 拓扑）

- **wrapper 存在性 + 语法**：`db/mysql_scripts/docker-entrypoint-wrapper.sh` 存在，`bash -n` **通过** ✅
- wrapper 主流程：`ensure_app_user()`（prj_user）+ `ensure_class_user()`（class_user）+ `ensure_root_sha2()` + `run_migration()`，经 base `entrypoint` 覆盖调用 ✅
- `ensure_app_user()` 用 `SPRING_DATASOURCE_PASSWORD`（容器值=`.env.dev`）、`ensure_class_user()` 用 `CLASS_DB_PWD`（容器值=`.env.prod` 经 `--env-file` 解析）→ 与【E】凭证契约一致，保证 prod 后端/php 可连 ✅
- **`db/mysql_init` 顶层仅剩 `.sql`**（init.sql / migrate_role.sql / msg.sql / work.sql）；`init.template.sql.template` 虽在目录内，但扩展名非 `.sql`，**不会被 MySQL 官方 entrypoint 自动执行** ✅（已确认无 `.sql.template` 软阻塞）

---

## 三、阻塞点清单

| 级别 | 条目 | 严重程度 | 说明 |
|---|---|---|---|
| 硬阻塞 | （无） | — | 合并合法、Dockerfile/挂载/变量/插值均通过，无启动即失败项 |
| 软阻塞 | Ollama 宿主未起 | 低 | AI 向量功能不可用；容器仍正常起。前置条件，非代码缺陷 |
| 软阻塞 | ARM64 首次构建耗时 | 低 | QEMU 仿真编译，预期耗时；非缺陷 |
| 建议 | CLASS_DB_PWD 字面不一致 + prod 头部注释未提 | 低 | 运行时自洽（靠 base `environment` 覆盖）；建议补注释或对齐两文件值 |
| 建议 | `dev-redis`/`prj-redis` 共享 `redis_data` 与 `dump.rdb` | 低 | 设计层面轻微耦合，不影响启动 |
| 建议 | prod 头部若干注释陈旧（redis 口令“有意不同”、CLASS_DB_PWD 未提） | 极低 | 误导性但非功能缺陷 |
| INFO | 存量 `mysql_data` 卷迁移 | 中（运维） | 全新卷首次初始化即自动建库/账号；已有卷需按 runbook 处理（wrapper 已幂等自愈 host 授权与认证插件） |

---

## 四、插值安全结果

- **结论：PASS** ✅
- 完整 `$` 发现（共 3 处，全部安全）：
  1. `MYSQL_ROOT_PASSWORD` 行：`$$aJs`（转义就位，`$`→字面量，安全）
  2. `PRJ_DB_PWD` 行：`$<`（`<` 非变量名首字符，字面量，安全）
  3. `SPRING_DATASOURCE_PASSWORD` 行：`$<`（同上，安全）
- 合并命令**未打印**任何 `variable is not set` 警告（stderr 空，exit 0）。

---

## 五、凭证契约结果

- **结论：PASS** ✅（prj_user 与 class_user 运行时均自洽）
- 对比值：
  - `SPRING_DATASOURCE_PASSWORD`：`.env.prod` == `.env.dev` == `<REDACTED-live-prod>` → **相等** ✅
  - `CLASS_DB_PWD`：`.env.prod`(`<REDACTED-live-prod>`) ≠ `.env.dev`(`ClassDev2026Xbox`) → **字面不等**；但运行时经 base `environment: CLASS_DB_PWD: ${CLASS_DB_PWD}`（`--env-file .env.prod`）统一解析，合并 `config` 证实 `dev-mysql` 与 `prj-php` 均为 `<REDACTED-live-prod>` → **运行时一致** ✅
  - `prj-redis` 与 `prj-backend-c` 的 `REDIS_PASSWORD`：同源 `.env.prod` → **自洽** ✅

---

## 六、已知限制与外部依赖

1. **ARM64 / Mac 运行时验证缺失**：本环境为 Windows/Intel，仅做静态核查；真实 ARM64 镜像构建与 Mac 运行需 Mac M2/OrbStack 实测。
2. **Ollama 前置**：宿主需先 `ollama serve` 且 `ollama pull bge-m3`；否则 AI 向量接口 503/超时（容器不因此崩溃）。
3. **ARM64 首次构建耗时**：多阶段 Maven/NPM 在 QEMU 下构建较慢，属预期。
4. **存量数据卷**：若复用已有 `mysql_data`，账号 host 授权与认证插件由 wrapper 幂等自愈；建库由 `init.sql` 仅在**全新卷**首启执行（存量卷需按 runbook 补库）。
5. **共享 redis_data**：`dev-redis` 与 `prj-redis` 共享同一数据目录，留意 `dump.rdb` 并发写（设计耦合，非启动阻塞）。

---

## 七、智能路由判定

- **判定：NoOne**
- 理由：本次为纯静态核查，未发现任何源码/配置 Bug。
  - 合并合法（exit 0、7 服务、端口零冲突、无插值警告）；
  - 3 个 Dockerfile 与 12 个挂载源全部存在；
  - 插值安全 PASS（无意外变量替换）；
  - 凭证契约 PASS（prj_user/class_user/redis 运行时自洽）；
  - platform 全 arm64、无 x86-only 指令。
- 唯一软风险（`CLASS_DB_PWD` 字面不一致 + 注释缺口）**不构成缺陷**，运行时经 base `environment` 覆盖自洽，故不路由 Engineer，仅作为建议项记录。

---

## 八、附录：合并 `config` 关键输出摘录

- 退出码 `0`，无 WARNING/stderr。
- 服务：nginx-gateway / dev-mysql / dev-redis / prj-redis / prj-backend-c / prj-frontend / prj-php（7 个）。
- 宿主端口：80、443、33060、63790、8081、1181（prj-redis、prj-backend-c 无宿主端口）。
- `dev-mysql` 实际 `CLASS_DB_PWD = <REDACTED-live-prod>`（证实 base `environment` 覆盖生效）。
- `prj-redis` / `prj-backend-c` 的 `REDIS_PASSWORD = <REDACTED-live-prod>`（同源）。
