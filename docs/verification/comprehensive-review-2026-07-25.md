# X-box 项目综合审查报告（六维度）

> 审查日期：2026-07-25 ｜ 审查范围：`/Users/crh123dexiaohao/X-box`
> 审查方式：静态扫描（Grep / Read）+ compose / 配置 / 源码交叉核验
> 严重度分级：**致命** / **严重** / **一般** / **建议**

---

## 0. 总览：历史问题处置状态

| 编号 | 历史问题 | 当前状态 | 证据 |
|---|---|---|---|
| O1 | init.sql 硬编码 CREATE USER | ✅ 已修复 | 账号改由 wrapper 注入 |
| F-02 | 前端监听端口错误 | ✅ 已修复 | `web/prj-frontend/nginx.conf` listen 8081 |
| F-03 | 后端服务名错误 | ✅ 已修复 | compose 服务名统一 |
| F-04 | fastjson2 autoType | ✅ 已修复 | `RedisConfig.java:114` 改用 `AUTO_TYPE_WHITELIST` |
| F-05 | X-Forwarded-For 可伪造 | ✅ 已修复 | nginx 两配置均为覆盖式 `$remote_addr` |
| R-15 | .dockerignore 漏排除 Niu_Txl | ✅ 已修复 | `.dockerignore:11` 已排除 |
| R-02 | prod 复用 dev 基础设施 | ⚠️ 仍存（设计张力） | 见 §2 / §4 |
| R-10 | Niu_Txl 字符集 utf8 | ⚠️ 仍存 | `msg.sql`/`work.sql` 表级 `DEFAULT CHARSET=utf8` |
| R-12 | prod 默认库名 prj_prod | ⚠️ 仍存（footgun） | `application-prod.yml:21` |
| R-08 | 前端缺 .env 文件 | ⚠️ 降级为"可用但欠文档" | `request.js:10` axios same-origin 兜底 |
| F-10 | Ollama 硬编码 | ⚠️ 已参数化，残留默认服务名 | `OkHttpOllamaEmbedClient.java:43` |
| F-11 | JWT 非 HttpOnly Cookie | ❌ 未修复 | `auth.js:7-8` |

**结论**：安全姿态整体良好，历史上多数 P0/P1 已落地；当前真正需要处理的集中在 **环境隔离（R-02）、前端 Token 存储（F-11）、凭证契约自动化校验、Niu_Txl 整洁度** 四项。

---

## 1. 项目目录结构与文件组织

**评价：结构清晰、分层合理。** 根目录 `backend / web / gateway / db / Niu_Txl / docs / scripts / test_data` 职责分明；compose 采用 `base + business-prj.dev/prod` 分层编排；文档体系完整（`docs/architecture|verification|security|deployment|audit`）。

### 一般 · 遗留文件未清理（Niu_Txl）
- 共 141 个文件，含大量非源码资产：
  - Dreamweaver 元数据目录 `_notes/`（18+ 个 `*.mno` 缓存文件，分布于 `902/message/_notes`、`902/work/_notes`、`902/work/admin/_notes`）
  - 复制件：`Niu_Txl/902/index copy.php`、`Niu_Txl/902/gd2 copy.php`
  - 个人实验页：`Niu_Txl/607/Dad's/`（index.html / 实验.html / 50x.html 等）
  - 测试数据：`Niu_Txl/607/places/top-1mcsv.zip`（百万域名 CSV）
- 上述均未纳入 `.gitignore`（现有规则仅排除 `607/永远的607`、`902/message/images`、`tinymce`、`work/admin/upload`），会随源码提交进版本库，增加噪音与误改风险。
- **建议**：
  1. 删除 `_notes/`、`* copy.php`、`Dad's/`、`top-1mcsv.zip` 等纯遗留资产；
  2. 在 `.gitignore` 追加 `Niu_Txl/**/_notes/`、`**/* copy.php`、`Niu_Txl/607/Dad's/`、`*.zip`（按业务确认）；
  3. 用 `git rm --cached` 清理已误跟踪项。

### 建议 · `.dockerignore` 误伤文档
- `.dockerignore:13` 的 `*.md` 会排除根目录 `VERSION.md`（及潜在 `README.md`）。后端多阶段构建上下文为仓库根，此排除对后端无害，但若未来需随镜像分发说明文档会被静默丢弃。
- **建议**：改为精确排除（如 `docs/` 已排除，无需 `*.md`），或在需要时显式放行 `!VERSION.md`。

---

## 2. 依赖关系

**评价：无循环依赖，依赖方向单向。** `gateway/nginx → {prj-frontend, prj-backend-c, prj-php}`；`prj-backend-c → mysql/redis`；`prj-php → mysql`；`Niu_Txl → mysql`。

### 严重 · R-02 prod 与 dev 零环境隔离（架构耦合）
`docker-compose.prod.yml` 直接复用 base 的 `dev-mysql` / `dev-redis` / `dev-network`（容器名 dev-mysql / dev-redis，数据卷 `mysql_data` 共享，见文件头注释第 28-37 行）。
- **风险**：
  1. dev 执行 `docker compose down -v` 或 reset 会**清空 prod 数据**（同卷）；
  2. dev / prod 共享同一 MySQL 实例，数据互相可见，违背环境隔离原则；
  3. prod 文件**非自包含**，必须配合 base 才能运行，部署脆弱（单独起 prod 会因 dev-mysql 不存在而失败）；
  4. 凭证契约跨 `.env.dev` ↔ `.env.prod.backend`（见 §4），无自动化校验。
- 说明：你的目标是"dev→prod 拓扑/端口完全对齐"，**网络拓扑对齐**与**数据卷隔离**是两件事，可在保留拓扑对齐的同时把数据卷拆开。
- **建议（分步）**：
  1. 短期：base 的 `mysql_data` 改为按环境命名（如 `dev_mysql_data` / `prod_mysql_data`），并在 `docker-compose.prod.yml` 用 `volumes: !reset` 覆盖为 prod 专用卷；
  2. 中期：prod 自带独立 `prod-mysql`/`prod-redis` 服务（同镜像同拓扑），彻底解耦；
  3. 在 prod 文件头把"必须配合 base"的约束写进 CI 校验或启动脚本前置检查。

### 一般 · prod `depends_on` 仅 `service_started`
- `docker-compose.prod.yml:78-80` 业务服务 `depends_on` mysql/redis 为 `condition: service_started`；而 dev 已用 `service_healthy`。DB 进程起来但端口未就绪时，prod 后端可能连接失败。
- **建议**：prod 也对齐为 `condition: service_healthy`，复用 base 中 redis 的 healthcheck（当前 redis 无 healthcheck，见既有 R- 登记，建议补 `redis-cli ping`）。

### 建议 · AI 嵌入默认服务名不在编排内
- `OkHttpOllamaEmbedClient.java:43`：`@Value("${AI_SERVICE_URL:http://dev-prj-llama:11434}")`，默认指向 `dev-prj-llama`，但 `ai_llama` 已被 `.dockerignore` 排除、未在当前 compose 编排。prod 若启用向量化却未显式设 `AI_SERVICE_URL`，会连不上。
- **建议**：prod 部署文档明确 `AI_SERVICE_URL` 必填项；或在 `@Value` 默认值改为 `http://host.docker.internal:11434`（宿主原生 Ollama，与 `application.yml:8` 注释一致）。

---

## 3. 代码质量

**评价：安全编码基线达标。** SQL 注入（GetSQLValueString + `mysqli_real_escape_string` + `sprintf %s` 参数化）、XSS（`htmlspecialchars` 输出转义）、fastjson2 白名单、异常处理（`request.js:27` Promise.reject 防吞）均已落实。

### 一般 · Niu_Txl 冗余/未完成模块
- 复制件 `index copy.php` / `gd2 copy.php`、实验页 `Dad's/`、Dreamweaver `_notes/` 属技术债（见 §1）。
- 仍混用 `mysql_*` 兼容垫片（为兼容旧 PHP 8.2 前的 Dreamweaver 产物），建议逐步统一到 `mysqli`/`PDO`。

### 建议 · 旧 PHP 模块长期演进
- Niu_Txl（607/902 班级网站）作为旧 PHP 模块与 Spring Boot 单体并存，建议长期抽离为独立服务或重写，降低耦合与维护心智负担。

---

## 4. 配置文件

**评价：配置治理整体规范。** `.env.*`（真实值，gitignore 屏蔽）+ `.env.*.example`（模板，可提交）双文件拆分到位；真实 `.env` 已被 `.gitignore` 与 `.dockerignore:16` 屏蔽。

### ✅ 已对齐：prod 凭证契约（原 P0）
- `.env.prod:37` `PRJ_DB_PWD=P7rJk3mL9nQ2wV5xB8cD1fH4aE6yU0tZcW2vM`
- `.env.prod.backend:12` `SPRING_DATASOURCE_PASSWORD=P7rJk3mL9nQ2wV5xB8cD1fH4aE6yU0tZcW2vM`
- `.env.dev:14` `SPRING_DATASOURCE_PASSWORD=P7rJk3mL9nQ2wV5xB8cD1fH4aE6yU0tZcW2vM`
- 三者一致，且 wrapper（`docker-entrypoint-wrapper.sh:172-173,184-185`）以 `SPRING_DATASOURCE_PASSWORD` 注入 `dev-mysql` 的 `prj_user` 口令，prod 后端可连。**原 P0（prod 连库口令不一致）已解决。**

### ✅ 已收敛：prod mysql 端口
- `docker-compose.prod.yml:156-162` `mysql: ports: !reset`，prod 不发布宿主 MySQL 端口（规避 WinNAT 33060 冲突 + 减少暴露面）。

### 严重（衍生）· 凭证契约靠注释维系，缺自动化校验
- 真正的约束链是 **`.env.dev` ↔ `.env.prod.backend`** 的 `SPRING_DATASOURCE_PASSWORD` 必须相等（`.env.prod` 的 `PRJ_DB_PWD` 为同值冗余项）。目前仅靠 `docker-compose.prod.yml:31` 人肉注释约束，无任何脚本校验。一次改口令漏改一处，prod 静默连不上或 dev/prod 耦合泄漏。
- **建议**：增加启动前置校验脚本（比对三处口令哈希一致），或引入 `dotenv-linter` / 配置契约测试纳入 CI。

### 一般 · dev 配置文件含弱/占位值（`.env.backend`）
- `.env.backend:11` `SPRING_DATASOURCE_PASSWORD` 已强值覆盖 ✅
- `.env.backend:17` `REDIS_PASSWORD=redis_default_pass_change_me`（= `application.yml:68` 默认值）
- `.env.backend:20` `JWT_SECRET=change-me-to-a-strong-256-bit-secret-key`（占位可猜测串）
- `.env.backend:24` `DRUID_PASSWORD=Druid@Dev2024`（= `application.yml:58` 默认值）
- dev 依赖"低风险"假设兜底；但 `JWT_SECRET` 占位值一旦误用于 prod 即灾难（可伪造任意 token）。
- **建议**：dev 也设强随机值；并在 `StartupSecurityValidator` 的弱值表里追加 `change-me-to-a-strong-256-bit-secret-key`，使其即便在 dev 也 fail-fast。

### 一般 · R-12 `application-prod.yml:21` 默认库名错位
- `url: ${SPRING_DATASOURCE_URL:jdbc:mysql://mysql:3306/prj_prod?...}`，默认 `prj_prod` 与 prod 实际 `prj_dev`（`docker-compose.prod.yml:68` 用 `${MYSQL_DATABASE:-prj_dev}` 覆盖）不一致。当前靠环境变量覆盖生效，**默认值是 footgun**：若 env 漏配会连错库。
- **建议**：将默认值改为 `prj_dev`，或在无 `SPRING_DATASOURCE_URL` 时 fail-fast（与 prod 严格模式一致）。

### 建议 · R-08 前端缺 `.env` 文件（可用但欠文档）
- `web/prj-frontend/` 下无 `.env.development` / `.env.production`；`request.js:10` `baseURL: process.env.VUE_APP_BASE_API` 未定义时为 `undefined` → axios 走 same-origin，配合 `vue.config.js` devServer 代理与 nginx 同源转发**实际可用**。
- `vue.config.js:34` 注释称"VUE_APP_BASE_API 已改为同源相对路径('/')"与事实不符（并未在任何 .env 设置）。
- **建议**：补 `.env.production` 显式 `VUE_APP_BASE_API=/`，消除歧义并文档化；prod build 时不依赖隐式兜底。

---

## 5. 安全与性能

**评价：安全加固项大多已落地。** init.sql 去密、fastjson2 白名单、X-Forwarded-For 覆盖、CSP 头、StartupSecurityValidator 弱凭证 fail-fast（prod）、Druid `/druid/**` 要求 ADMIN 角色、prod mysql 端口收敛均已完成。

### 严重 · F-11 JWT 存于非 HttpOnly Cookie（`auth.js`）
- `auth.js:3` TokenKey=`Admin-Token`；`:7` `sameSite:'Lax'`；`:8` `secure: window.location.protocol==='https:'`。
- **问题**：Cookie 无 `HttpOnly` → 任意 XSS 可 `document.cookie` 窃取 token；`secure` 仅在 HTTPS 生效，HTTP 明文可被链路嗅探。
- **建议（二选一）**：
  1. 改 Cookie 为 `HttpOnly + Secure + SameSite=Strict`（前端改从响应头或内存取 token，需配合后端在登录接口种 Cookie）；
  2. 直接采用后端已支持的 `Authorization: Bearer` 头（`SecurityConfig` header 标识为 `Authorization`，`request.js:20` 已拼接 Bearer），放弃 Cookie 存储。优先推荐方案 2，与既有后端设计一致、天然防 XSS 读取。

### 一般 · R-10 Niu_Txl 字符集 utf8（非 utf8mb4）
- `Niu_Txl/902/msg.sql:22,34,52,79` 与 `work.sql:22,34,50,79`：库/表 `DEFAULT CHARACTER SET utf8`（连接层虽 `SET NAMES utf8mb4`，但表级仍是 3 字节 utf8）。
- **问题**：emoji / 生僻四字节字符会被截断或报错。
- **建议**：`ALTER DATABASE` / `ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`；PHP 连接层已 `SET NAMES utf8mb4`，无需改代码。

### 建议 · Redis 库号与连接池
- `application.yml:70` `database: 9` 固定库号——多环境共用同一 redis 实例时需注意键空间隔离，建议按环境用不同 db 或加 key 前缀。
- `application.yml:50` Druid `maxActive: 20` 偏小，高并发接口（Excel 比对/下载）易排队；建议按压测结果上调并配 `maxWait`。

### 建议 · dev redis 弱口令
- dev 用 `REDIS_PASSWORD` 默认值（`redis_default_pass_change_me`），虽 `requirepass` + loopback 限制暴露面，仍建议 dev 也设强值，避免"默认值即口令"的坏习惯扩散。

---

## 6. 可维护性与可扩展性

**评价：可维护性良好。** 分层清晰、compose 分层、文档齐全；`docker-entrypoint-wrapper.sh` 幂等自愈（`ensure_app_user`/`ensure_class_user`/`ensure_root_sha2`）、CRLF `\r` 剥离防御、my.cnf 600 权限防泄露，均体现工程化成熟度。

### 严重 · R-02（环境隔离，见 §2）
- dev/prod 共享基础设施，违背"环境隔离"最佳实践，多租户/多环境扩展困难，回滚风险高。是制约可扩展性的首要瓶颈。

### 一般 · 凭证契约隐性
- 三文件注释约束、缺自动化校验（见 §4）。建议引入配置契约校验，降低运维出错率。

### 建议 · 模块边界
- Niu_Txl（旧 PHP）与 Spring Boot 单体并存，建议长期抽离为独立服务或重写，降低耦合与维护心智负担。

---

## 7. 优先行动清单（按严重度）

| 优先级 | 项 | 动作 | 文件/位置 |
|---|---|---|---|
| 严重 | F-11 | JWT 改 Bearer 头或 HttpOnly+Secure+SameSite=Strict Cookie | `web/prj-frontend/src/utils/auth.js:3,7,8` |
| 严重 | R-02 | prod 数据卷与 dev 解耦（卷命名拆分），中期独立 prod 实例 | `docker-compose.base.yml` / `docker-compose.prod.yml:28-37` |
| 严重 | 凭证契约 | 加启动前置校验脚本比对 `.env.dev`↔`.env.prod.backend` 口令 | 新增 scripts/ |
| 一般 | R-10 | 库/表转 utf8mb4 | `Niu_Txl/902/*.sql` |
| 一般 | R-12 | 默认库名改 prj_dev 或 fail-fast | `application-prod.yml:21` |
| 一般 | dev 弱值 | `.env.backend` 设强 JWT/Redis/Druid，弱值表追加占位串 | `.env.backend:17,20,24` |
| 一般 | Niu_Txl 整洁 | 清理 _notes/copy/Dad's/zip + 补 .gitignore | `Niu_Txl/**` |
| 一般 | prod depends_on | 对齐 service_healthy + 补 redis healthcheck | `docker-compose.prod.yml:78-80` |
| 建议 | R-08 | 补 `.env.production` 显式 VUE_APP_BASE_API=/ | `web/prj-frontend/` |
| 建议 | .dockerignore | `*.md` 改为精确排除，放行 VERSION.md | `.dockerignore:13` |
| 建议 | AI 嵌入 | prod 文档标注 AI_SERVICE_URL 必填 / 改默认 host.docker.internal | `OkHttpOllamaEmbedClient.java:43` |
| 建议 | Redis/连接池 | 按环境分库、上调 maxActive | `application.yml:50,70` |

---
*本报告基于静态扫描与配置交叉核验，未执行运行时渗透测试。建议对 F-11（Token 存储）与 R-02（环境隔离）优先排期。*

---

## 8. 修复执行记录（2026-07-25）

以下项已在本轮落地修复（对应前文分级）：

| 项 | 严重度 | 修复内容 | 文件 / 位置 |
|---|---|---|---|
| R-12 | 一般 | 默认库名 `prj_prod` → `prj_dev`（与 prod 实际库名对齐） | `application-prod.yml:21` |
| R-10 | 一般 | 新增 utf8mb4 迁移脚本（msg/work 库及 `admin_user`/`post`/`reply`/`work_pic`/`work_type` 表） | `db/mysql_init/migrate_utf8mb4.sql`（新增） |
| dev 弱值 | 一般 | JWT_SECRET / DRUID_PASSWORD 轮换强值；REDIS_PASSWORD 在 `.env.dev` 与 `.env.backend` 联动轮换（dev redis 真实口令来源为 `.env.dev`） | `.env.backend:17,20,24`、`.env.dev:28` |
| .dockerignore | 建议 | 移除裸 `*.md`，避免误伤根目录 `VERSION.md` | `.dockerignore:13` |
| prod depends_on + 健康检查 | 一般 | prod `depends_on` 改 `service_healthy`；base mysql/redis 补 healthcheck（纯增量，不改动现有行为） | `docker-compose.prod.yml:77-81`、`docker-compose.base.yml` |
| Niu_Txl 整洁 | 一般 | 删除 `_notes/`、`* copy.php`、`top-1mcsv.zip`；`.gitignore` 增补排除（Dad's/ 仅 gitignore，未删） | `Niu_Txl/**`、` .gitignore` |
| 凭证契约 | 严重衍生 | 新增启动前校验脚本，已运行通过（dev/prod.backend prj_user 口令一致） | `scripts/verify-credential-contract.sh`（新增，已 chmod +x） |
| **F-11** | **严重** | **后端登录种 `HttpOnly+Secure+SameSite=Strict` Cookie；登出清除；`TokenService` 兼容读 Cookie。前端 `auth.js` 改为 localStorage 登录态标记（不再持有原始 JWT）；`request.js` 移除手动 `Authorization` 头（改由同源 Cookie 自动携带）** | `LoginController.java`、`LogoutSuccessHandlerImpl.java`、`TokenService.java`、`web/.../auth.js`、`web/.../request.js`、`web/.../store/modules/user.js` |
| **构建阻塞·平台不匹配** | **严重（阻断构建）** | **后端 Dockerfile 基础镜像 `eclipse-temurin:17-jdk-alpine` / `17-jre-alpine` 仅有 amd64 清单，Apple Silicon(M2) 构建报 `no match for platform in manifest: not found`。已统一改为非 alpine 版 `eclipse-temurin:17-jdk` / `17-jre`（含 arm64/amd64 多架构，M2/Intel/Windows 均可原生构建）；包管理 `apk`→`apt`，运行阶段补装 `wget`（HEALTHCHECK 需要），非 root 用户创建改用 `groupadd/useradd`** | `backend/prj-backend-c/Dockerfile.dev:8,11`、`backend/prj-backend-c/Dockerfile.prod:10,13,23,36-41` |

### 待办 / 需确认
1. **R-02（prod/dev 数据卷解耦）**：涉及现有 prod 数据迁移风险，本轮**未动**，需你明确确认后再实施（建议先把 `mysql_data` 按环境命名拆分，中期独立 prod 实例）。
2. **F-11 需编译 + 登录冒烟测试**：后端为 Java/Spring Boot 改动，需 `mvn compile` 通过，并在 dev/prod 实测登录→访问→登出全链路（验证 HttpOnly Cookie 下发、同源自动携带、XSS 无法读取）。
3. **Dad's/ 个人目录**：已加入 `.gitignore`（不再入库），是否物理删除待你决定。
4. **StartupSecurityValidator 弱值表**：建议补入 `change-me-to-a-strong-256-bit-secret-key`（dev 已轮换强值，此项为纵深防御，可选）。
5. **启动命令须带 `--env-file .env.dev`**：dev 编排头部已注明正确命令 `docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d --build`。漏带会导致 `CLASS_DB_PWD`/`REDIS_PASSWORD` 顶层插值告警并填空白（class_user 口令注入异常）。`.env.dev` 已含这两个变量。
