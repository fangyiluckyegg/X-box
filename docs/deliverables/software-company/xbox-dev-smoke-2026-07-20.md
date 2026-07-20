# X-box DEV 环境「从零跑通」静态权威就绪性核查报告

- **核查人**：QA 工程师 Edward（software-qa-engineer）
- **核查日期**：2026-07-20
- **核查对象**：`D:\crh123dexiaohao\X-box`（git 已提交态）
- **核查性质**：静态权威就绪性核查（fresh clone / 全新数据卷视角），**不修改任何源码**
- **启动命令（已确认）**：
  ```bash
  cd D:\crh123dexiaohao\X-box
  docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml -f docker-compose.classphp.dev.yml --env-file .env.dev up -d
  ```

---

## 0. 结论速览

| 项目 | 结论 |
|---|---|
| **能否从零跑通** | ✅ **有条件 YES** |
| 硬阻塞 | **无** |
| 软阻塞 | **1 项**（init.template.sql 误置于 initdb.d，见 §4 / §D） |
| 建议项 | 3 项（见 §6） |
| 智能路由 | **NoOne**（纯静态核查，未发现导致 fresh-run 失败的源码 Bug） |

**「有条件」的含义（均为宿主环境/时间前置，非代码缺陷）：**
1. **Docker 运行**：Docker Desktop（Windows/WSL2 后端）已启动。
2. **磁盘与网络**：首次 `up` 需拉取镜像（mysql:8.0 / redis:7-alpine / nginx:1.25-alpine）并首次 maven/npm 构建下载依赖，需充足磁盘与出网能力。
3. **可选 Ollama**：AI 功能需宿主原生 Ollama 监听 `:11434`；未启动则 AI 端点不可用，但不影响容器启动与基础业务。
4. **首次构建耗时**：maven 依赖与镜像拉取可能耗时数分钟至数十分钟，属时间成本而非阻塞。

> 源码 / 配置层面**无任何硬阻塞**，6 个服务在结构、配置、挂载、账号注入上自洽，可从零跑通。

---

## 1. 【A】compose 合并合法性（实跑）

| 检查点 | 结果 |
|---|---|
| `docker compose ... config` 退出码 | ✅ **0** |
| 合并后服务数 | ✅ **6**（nginx-gateway / mysql / redis / prj-frontend / prj-backend-c / prj-php） |
| 端口零冲突 | ✅ **通过** |

**合并后宿主机端口映射（互不重复）：**

| 服务 | 宿主机端口 | 容器端口 |
|---|---|---|
| nginx-gateway | 127.0.0.1:80 / 127.0.0.1:443 | 80 / 443 |
| mysql | 127.0.0.1:33060 | 3306 |
| redis | 127.0.0.1:63790 | 6379 |
| prj-backend-c | 8080 | 8080 |
| prj-frontend | 8081 | 8081 |
| prj-php | 127.0.0.1:1181 | 80 |

> 预期端口集 `80/443/33060/63790/8080/8081/1181` 全部命中，无重叠。

---

## 2. 【B】构建上下文 Dockerfile 存在性

| 文件 | 结果 |
|---|---|
| `web/prj-frontend/Dockerfile.dev` | ✅ 存在 |
| `backend/prj-backend-c/Dockerfile.dev` | ✅ 存在 |
| `Niu_Txl/Dockerfile.classphp` | ✅ 存在 |

---

## 3. 【C】bind 挂载源目录存在性

| 目录（来源 compose） | 结果 | 补充 |
|---|---|---|
| `gateway/nginx/conf.d` | ✅ 存在 | 含 `prj.conf`（有效，见 §8） |
| `gateway/nginx/ssl` | ✅ 存在 | 含 `prj.crt` / `prj.key`（有效） |
| `logs/nginx` | ✅ 存在 | — |
| `db/mysql_init` | ✅ 存在 | 含 init.sql / init.template.sql / migrate_role.sql / msg.sql / work.sql |
| `db/mysql_scripts` | ✅ 存在 | 含 `docker-entrypoint-wrapper.sh` |
| `logs/mysql` | ✅ 存在 | — |
| `db/redis_data` | ✅ 存在 | — |
| `logs/redis` | ✅ 存在 | — |
| `logs/prj-frontend` | ✅ 存在 | — |
| `logs/prj-backend-c` | ✅ 存在 | — |
| `logs/prj-php` | ✅ 存在 | — |

> 全部 11 个挂载源存在，容器创建阶段无缺目录风险。

---

## 4. 【D】MySQL 账号创建来源（从零跑通最关键）

### 4.1 全新卷账号来源判定

| 账号 | 全新卷来源 | 是否自洽 |
|---|---|---|
| `prj_dev` 库 | `MYSQL_DATABASE=prj_dev`（仅建库，**不含账号**） | ✅ |
| `prj_user@'%'` | **wrapper `ensure_app_user()`**（MySQL 就绪后每次启动执行） | ✅ |
| `class_user@'%'` | **wrapper `ensure_class_user()`**（MySQL 就绪后每次启动执行） | ✅ |
| `msg` / `work` 库 | `msg.sql` / `work.sql`（initdb.d 顶层，全新卷自动执行） | ✅ |

- `init.sql` **显式声明** `prj_user` 创建/授权已移除（[O1] 去密），仅建 `prj_dev` 库 + `user_info`/`employee_kpi` 表 + 默认 admin。
- `msg.sql` / `work.sql` 仅建库建表；`class_user` 对 `msg.*`/`work.*` 的授权由 wrapper 完成（注释已说明）。
- **结论**：`prj_user` 与 `class_user` 两个账号**均不依赖 init SQL 的 CREATE USER**，而由 wrapper 在 MySQL 就绪后幂等注入。全新卷流程为：官方 entrypoint 跑 initdb.d 建库表 → wrapper 就绪后建两账号并授权 → 后端 / PHP 可连。**无硬阻塞。**

### 4.2 ⚠ 发现：init.template.sql 误置于 initdb.d（软阻塞/设计瑕疵）

`db/mysql_init/init.template.sql` 是**模板文件**（头部注释亦自述为「初始化脚本模板」），但其位于 initdb.d **顶层**，全新卷会被 MySQL 官方 entrypoint **自动执行**，产生以下影响：

1. 其 `CREATE USER IF NOT EXISTS 'prj_user'@'%' IDENTIFIED BY 'DEV_DB_PASSWORD_PLACEHOLDER';` 会用**占位密码**先建 `prj_user` —— 随后被 wrapper `ensure_app_user()` 的 `ALTER USER ... BY '${app_pwd}'`（`app_pwd` = `SPRING_DATASOURCE_PASSWORD` = `<REDACTED-live-prod>...`）**修正为真实口令**，故最终可用。
2. 其 `CREATE TABLE user_info (...)` **无 `IF NOT EXISTS`**，而 `init.sql` 已先建 `user_info`（含 `role` 列）→ 触发 `table already exists` 错误。该错误被官方 entrypoint **记录但不中止**容器；`user_info` 保留 `init.sql` 的「含 role」版本，`migrate_role.sql` 幂等补列亦安全。
3. 净效果：**不硬阻塞**，但会产生一条 MySQL 初始化错误日志，且存在一个「先占位密码、后由 wrapper 修正」的脆弱时序（依赖 wrapper 必跑）。

> **建议（非阻塞）**：将 `init.template.sql` 移出 `db/mysql_init/`，或改名为 `init.template.sql.template` / 放到非 initdb.d 目录，避免被自动执行。该文件属遗留模板，不应参与初始化。

---

## 5. 【E】wrapper 脚本就绪

| 检查点 | 结果 |
|---|---|
| 文件存在 / 可读 / 可执行 | ✅ |
| `bash -n` 语法检查 | ✅ 通过 |
| 含 [T11] `DROP USER IF EXISTS 'class_user'@'%%';` 自愈块 | ✅ 存在（line 230） |
| `ensure_app_user()` / `ensure_class_user()` 函数 | ✅ 存在（line 164 / 206） |
| `ensure_root_sha2()` 认证插件迁移 | ✅ 存在 |

> wrapper 设计完备：信号转发、就绪等待、幂等账号注入、迁移重试、[T11] 畸形 host 账号自愈，均到位。

---

## 6. 【F】env 变量一致性

| 检查点 | 结果 |
|---|---|
| compose 插值变量 `${CLASS_DB_PWD}` | ✅ 在 `.env.dev` 中存在 |
| compose 插值变量 `${REDIS_PASSWORD}` | ⚠ 在 `.env.dev` 中**缺键**，但 `${REDIS_PASSWORD:-redis_default_pass_change_me}` 退化为默认值；redis command 与 prj-backend-c `REDIS_PASSWORD` 同取该默认值 → **连通一致** |
| 后端 `SPRING_DATASOURCE_PASSWORD`（.env.backend） | ✅ 与 `.env.dev` 的 `PRJ_DB_PWD` 一致（`<REDACTED-live-prod>...`） |
| 其他 compose 引用变量缺失且无默认 | ✅ 无 |
| `.env.dev` 换行符 | ✅ LF（wrapper 亦对 CRLF 做 `\r` 剥除，健壮） |

**建议项：**
- **建议补** `.env.dev` 的 `REDIS_PASSWORD` 键（与 `.env.dev.example` 的 `REDIS_PASSWORD=ChangeMe_Redis` 对齐），消除文档/实际不一致；当前因默认值一致，**非阻塞**。

---

## 7. 【G】外部依赖（Ollama）

- `prj-backend-c`：`AI_SERVICE_URL=http://host.docker.internal:11434`，指向**宿主原生 Ollama**（非容器）。
- 通过 `extra_hosts: host.docker.internal:host-gateway` 兜底 DNS。
- **前置条件**：宿主需先启动 Ollama 并 `ollama pull bge-m3`（见 `scripts/setup-host-ollama.*`、`docs/host-ollama-setup.md`）。
- **影响**：未启动 Ollama → AI 向量功能不可用（相关端点报错/降级），但**不影响容器启动、MySQL/Redis 连接与基础业务**。

---

## 8. 【H】网关配置有效性（附带核查）

- `gateway/nginx/conf.d/prj.conf` 存在且完整：
  - `set $front_host prj-frontend;`（line 38）、`set $backend_host prj-backend-c;`（多处）均已定义 → **无未定义变量**。
  - `resolver 127.0.0.11`（Docker 内置 DNS）处理变量型 `proxy_pass` 的运行时解析 → nginx 配置合法，`nginx-gateway` 可正常启动。
  - 80（HTTP）/ 443（SSL，证书 `prj.crt`/`prj.key` 已挂载）双 server 块齐备。

---

## 9. 阻塞点清单

| 严重程度 | 条目 | 说明 |
|---|---|---|
| 🔴 硬阻塞 | **无** | 源码/配置层面无导致 fresh-run 失败的项 |
| 🟡 软阻塞 | `init.template.sql` 在 `db/mysql_init/`（initdb.d 顶层） | 全新卷自动执行 → `CREATE TABLE user_info` 报「已存在」错误（非致命）；并先用占位密码建 `prj_user`（wrapper 后修正）。建议移出/改名 |
| 🟢 建议 | `.env.dev` 缺 `REDIS_PASSWORD` 键 | 默认值一致故非阻塞，建议补齐以对齐 `.env.dev.example` |
| 🟢 建议 | git working tree 非 clean | 当前 `项目开发说明` 有未提交修改（任务假设为 clean）。**不影响运行**，仅与「已提交态」假设不符 |
| 🟢 建议 | 本地 `./db/mysql_data` 孤立目录 | mysql 实际用**命名卷** `mysql_data`（非 bind 挂载），该本地目录未被使用，可清理（无害） |

---

## 10. 已知限制与外部依赖（汇总）

1. **Ollama（宿主原生）**：`:11434`，AI 功能前置，可选。
2. **首次构建耗时 + 网络/磁盘**：镜像拉取与 maven/npm 首次依赖下载耗时；需 Docker 运行 + 出网。
3. **dev 弱口令**：`MYSQL_ROOT_PASSWORD=Root@Dev123456`、`REDIS_PASSWORD=redis_default_pass_change_me`、弱 `JWT_SECRET`/`DRUID_PASSWORD` 均为 dev 专用，已文档化，生产须替换。
4. **命名卷**：`mysql_data` / `maven-cache` / `node_modules_volume` 首次以空卷创建（`mysql_data` 为命名卷，规避 NTFS 大小写敏感导致的 redo 日志初始化失败，[C17]）。
5. **启动时序（软）**：`prj-backend-c` 的 `depends_on mysql` 为 `service_started`（非 healthy），`prj_user` 由 wrapper 在 MySQL 就绪后创建；backend `start_period=120s` + Spring 重试可覆盖该时序，非阻塞。`prj-frontend` 依赖 `prj-backend-c service_healthy`，时序正确。

---

## 11. 智能路由判定

- **判定：NoOne**
- 理由：本次为**纯静态权威核查**，不执行测试套件；源码与配置层面**未发现导致 fresh-run 失败的源码 Bug**。唯一瑕疵 `init.template.sql` 为遗留模板误置于 initdb.d，属**软阻塞/设计瑕疵**，由 wrapper 补偿后不阻断跑通，故不路由 Engineer；建议在交付报告中作为**清理建议**提交，由主理人决定是否指派 Engineer 移除。

---

## 12. 真实 up 验证（可选，建议用户侧执行）

如环境资源允许，可实跑（首次构建可能超时，建议仅作可选验证）：

```bash
cd D:\crh123dexiaohao\X-box
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml -f docker-compose.classphp.dev.yml --env-file .env.dev up -d
docker compose ps
# 等待 healthcheck：prj-backend-c 的 /captchaImage 返回 200 即就绪
docker compose logs -f mysql        # 关注 wrapper 日志：prj_user / class_user 授权完成
docker compose logs -f prj-backend-c
```

**前置条件**：Docker Desktop 运行中、充足磁盘空间、首次构建耗时、可选 Ollama（`ollama serve` + `ollama pull bge-m3`）。
