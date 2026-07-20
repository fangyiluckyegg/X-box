# X-box 容器化项目：dev→prod 配置对齐 + bug 排查 + 整合精简报告

> 报告日期：2026-07-20
> 执行人：软件工程师（寇豆码）
> 范围：`D:\crh123dexiaohao\X-box\` 全部 Compose / .env / Dockerfile / 后端 application 配置 / nginx 网关 / ollama·数据卷
> 目标环境：生产 = macOS Sonoma 14.8.7 + Mac mini M2（ARM64 / aarch64）+ OrbStack 2.1.3；宿主原生 Ollama + bge-m3。

## 0. 一句话摘要

本次对 X-box 生产编排做了系统性「dev→prod 对齐 + bug 排查 + 整合精简」：修复了 **2 处会导致 prod 直接启动失败的高危端口冲突**（网关 80/443、Redis 63790）、消除了 **prod 业务服务与网关之间的网络隔离**、将 **base Redis 升级到 arm64 兼容的 redis:7**、并补齐了前端端口映射与 Dockerfile 暴露端口等低级不一致；所有真实密钥/密码在 `.env.prod` 中原样保留未改动，dev 配置零破坏。

## 1. 问题统计（按优先级）

| 优先级 | 数量 | 条款 |
|--------|------|------|
| 高（High） | 2 | P1 网关端口冲突、P2 Redis 端口冲突 |
| 中（Medium） | 5 | P3 网络隔离、P6 CRLF/跨平台行尾风险、P12 class_init 未挂载、P14 base Redis 缺 arm64 清单（已修）、P15 冗余 Redis |
| 低（Low） | 9 | P4 前端 8081 未暴露、P5 .env 模板占位、P7 前端 EXPOSE 端口、P8 Java 版本文档漂移、P9 PHP 注释漂移、P13 macOS 绑定挂载权限、P16 compose 重复段、P17 .env.dev 冗余键、P18 compose CRLF |

合计 **16 条发现**（2 高 / 5 中 / 9 低），其中已直接修复 4 处（P1、P2、P3、P14、P4、P7 中已落地的项），其余给出修复/整合方案或列入待人工确认。

---

## 2. 按模块分类的问题清单

### 2.1 Docker Compose 主文件（docker-compose.prod.yml / docker-compose.base.yml）

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| Compose 主文件 | **网关端口冲突**：prod 的 `prj-nginx` 与 base 的 `nginx-gateway` 同时绑定 `127.0.0.1:80` 与 `127.0.0.1:443`；`base + prod` 合并启动仅一个能绑定成功，另一个容器起不来（端口已被占）。 | `docker-compose.prod.yml:46-64`（prj-nginx）<br>`docker-compose.base.yml:17-19`（nginx-gateway） | **已修复**：移除 `prj-nginx` 服务，统一复用 base 的 `nginx-gateway`（同一份 `gateway/nginx/conf.d/prj.conf`，TLS/资源限制完全一致）。prod 前端 `depends_on: nginx-gateway`。 | 高 |
| Compose 主文件 | **Redis 端口冲突**：prod 的 `prj-redis` 与 base 的 `redis` 同时绑定 `127.0.0.1:63790:6379`；合并启动冲突。 | `docker-compose.prod.yml:87-90`<br>`docker-compose.base.yml:93-94` | **已修复**：保留 prod 自有 `prj-redis`（自带强口令），但**移除其宿主机端口映射**，仅 dev-network 内可达（后端经服务名 `prj-redis` 访问）。 | 高 |
| Compose 主文件 | **网络隔离**：原 prod 业务服务挂在 isolated 的 `prj-network`，而网关在 `dev-network`；其中 `prj-frontend` 仅挂 `prj-network` → 网关（dev-network）无法代理到前端，功能断裂；后端/php 需同时挂两张网。 | `docker-compose.prod.yml:64,126-128,147,182-183`（prj-network）<br>顶层 `networks: prj-network` 见 `:185-187` | **已修复**：全部 prod 业务服务（`prj-backend-c`/`prj-frontend`/`prj-php`/`prj-redis`）统一挂 `dev-network`，删除 `prj-network` 定义，与 dev 拓扑一致。 | 中 |
| Compose 主文件 | **前端端口未对齐**：dev 的 `prj-frontend` 暴露 `8081:8081`，prod 未暴露，端口映射逻辑不一致。 | `docker-compose.prod.yml:130-147`（无 ports） | **已修复**：补回 `127.0.0.1:8081:8081`（loopback，与 dev 对齐）。 | 低 |

### 2.2 环境变量 /.env

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| 环境变量 | `.env.prod` 与 `.env.prod.example` 键名/结构已对齐（MYSQL_DATABASE、REDIS_PASSWORD、JWT_SECRET、DRUID_*、SWAGGER_ENABLED、AI_API_TOKEN、CLASS_DB_* 齐全），且 `.env.prod` 真实值经核验完整、可满足 `application-prod.yml` 的全部必填项，**无需改键**。**本次未改动 `.env.prod` 内容（保留全部真实密钥/密码）**。 | `.env.prod`（全文件）<br>`application-prod.yml:24,36,42` | 保持原样；仅提示用户上线前确认 `SPRING_DATASOURCE_PASSWORD` 与 Mac 本机 `.env.dev` 一致（凭证契约）。 | — |
| 环境变量 | **跨平台 CRLF 风险**：`docker-compose.base.yml` / `docker-compose.business-prj.dev.yml` / `docker-compose.business-prj.yml` 为 **CRLF**；`.env.*` 当前均为 LF。若 Mac 侧从 Windows 复制 `.env.prod` 带入 CRLF，`MYSQL_ROOT_PASSWORD`/`SPRING_DATASOURCE_PASSWORD`/`CLASS_DB_PWD` 会带尾随 `\r` → MySQL wrapper 已做 `\r` 剥离，但**后端 Spring 不会剥离 env_file 中的 `\r`**，导致 JDBC 鉴权失败。 | 多文件（见 P18） | **建议**：Mac 部署前对 `.env.prod`/`.env.dev` 执行 `sed -i 's/\r$//'`。当前仓库内 `.env.prod` 已是 LF，本次未改。 | 中 |
| 环境变量 | `.env.prod.example` 中 `REDIS_PASSWORD`/`JWT_SECRET`/`DRUID_PASSWORD`/`AI_API_TOKEN` 为字面量占位文本 `<openssl rand -base64 24 生成>`；若直接 `cp` 而不替换，compose 会把该含空格/`$`/`<>` 的字符串当作真实值插值，导致 Redis/后端启动异常。 | `.env.prod.example:25,28,32,38` | 属模板预期行为（文件头已声明“上线前替换”）；报告提示：**务必先生成真实值再部署**，不要直接复制未编辑的 example。 | 低 |
| 环境变量 | `.env.dev` 含冗余键 `MYSQL_ROOT_PWD`（MySQL 容器仅读取 `MYSQL_ROOT_PASSWORD`，前者未被消费）。 | `.env.dev:2-3` | 可清理（不影响功能）；建议随 dev 整理时删除。 | 低 |

### 2.3 business-prj compose

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| business-prj compose | `docker-compose.business-prj.yml` 与 `docker-compose.business-prj.dev.yml` 高度重复（同一 `prj-frontend`/`prj-backend-c` 定义），但前者注释仍称“开发/内网”、`SPRING_PROFILES_ACTIVE=dev`，文件名却无 `dev`，易与 prod 混淆；且前者未挂源码、无 healthcheck，与 dev 版行为不一致。 | `docker-compose.business-prj.yml:1-82`<br>`docker-compose.business-prj.dev.yml:1-111` | **整合建议**：将公共 service 定义抽到 `docker-compose.base.yml` 或用 YAML anchor；明确命名（prod 用 `docker-compose.prod.yml`，dev 用 `*.dev.yml`），避免歧义。本次未改（属 dev 侧重构，不在 prod 对齐范围内）。 | 低 |

### 2.4 classphp/php compose

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| classphp/php compose | `docker-compose.classphp.dev.yml` 的 `prj-php` 端口 `127.0.0.1:1181:80` 与 prod `prj-php` 一致（已对齐），`CLASS_DB_HOST=dev-mysql` 与 prod 一致，无错位。 | `docker-compose.classphp.dev.yml:24`<br>`docker-compose.prod.yml`(prj-php) | 经核对，**dev 与 prod 的 php 服务定义已对齐**，无需修改。 | — |

### 2.5 Dockerfile（后端 / 前端 / ai_llama / php）

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| Dockerfile(前端) | `web/prj-frontend/Dockerfile.prod` 写 `EXPOSE 80`，但 `nginx.conf` 实际 `listen 8081`，且网关 `prj.conf` 代理到 `prj-frontend:8081`；EXPOSE 与真实监听端口不符，易误导。 | `web/prj-frontend/Dockerfile.prod:20`<br>`web/prj-frontend/nginx.conf:18` | **已修复**：`EXPOSE 80` → `EXPOSE 8081`（与 nginx.conf / 网关代理目标一致）。 | 低 |
| Dockerfile(后端) | 后端 `Dockerfile.dev`/`Dockerfile.prod` 基础镜像为 `eclipse-temurin:17-jdk-alpine` / `17-jre-alpine`（官方多架构镜像，**ARM64 兼容 ✓**）；但《项目开发说明》写“eclipse-temurin:21”。属文档漂移，非镜像问题。 | `backend/prj-backend-c/Dockerfile.dev:8`<br>`backend/prj-backend-c/Dockerfile.prod:10,25`<br>`项目开发说明:132` | 文档更正即可；Dockerfile 保持 JDK17（兼容 Spring Boot 3），**本次未改 Dockerfile**。 | 低 |
| Dockerfile(PHP) | `Niu_Txl/Dockerfile.classphp` 注释称 “PHP 7.4”，实际 `FROM php:8.2-apache`（`php:8.2-apache` 为官方多架构镜像，**ARM64 兼容 ✓**）；注释与实现不符。 | `Niu_Txl/Dockerfile.classphp:2,5`<br>`Dockerfile.classphp.qa:3` | 注释更正为 8.2（不动 `FROM`，避免破坏）；**本次未改文件**，仅提示。 | 低 |
| Dockerfile(ai_llama) | `ai_llama/Dockerfile.llama` 基础镜像 `ollama/ollama:0.31.2`（官方多架构，**ARM64 兼容 ✓**），但自 2026-07-16 起 AI 推理已迁宿主原生 Ollama，该文件当前不被任何 compose 引用（仅 base 末尾注释块中保留回退示例）。 | `ai_llama/Dockerfile.llama:9` | 无功能影响；属历史遗留，可保留作回退。不纳入本次修复。 | — |

### 2.6 后端 application 配置

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| 后端 application | `application-prod.yml` 与 prod compose **完全对齐**：DB host 由 compose 注入 `dev-mysql`（`${SPRING_DATASOURCE_URL}` 默认 `mysql` 被覆盖）、Redis host `prj-redis`、密码/密钥均由 env 注入且必填 fail-fast；`AI_SERVICE_URL` 由 compose 注入 `host.docker.internal:11434`。无错位。 | `application-prod.yml:21,33,24,36,42` | 经核对**一致**，无需修改。 | — |
| 后端 application | dev `application.yml` 用 `mysql`（服务名）、prod `application-prod.yml` 用 `dev-mysql`（容器名）——二者在 `dev-network` 上均可解析（user-defined 网络同时支持服务名与容器名解析），**非 bug**；属两套环境命名差异，符合凭证契约设计。 | `application.yml:41`<br>`application-prod.yml:21` | 保持现状（已在 compose 显式注入 URL，application 内的默认值仅作兜底）。 | — |

### 2.7 nginx 网关

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| nginx 网关 | `gateway/nginx/conf.d/prj.conf` 用**服务名** `prj-frontend`/`prj-backend-c` 代理；dev 与 prod 的 service 名均为 `prj-frontend`/`prj-backend-c`（仅 container_name 带 `dev-` 前缀），故同一份 `prj.conf` 对 dev/prod 通用，无需分环境。**443 TLS 块证书已存在**（`gateway/nginx/ssl/prj.crt`、`prj.key`），网关可正常启用 HTTPS。 | `gateway/nginx/conf.d/prj.conf:38,49,74,117,128,150` | 经核对**无错位**；prod 复用 base `nginx-gateway` 后行为不变。 | — |
| nginx 网关 | `prj.conf` 未路由 `/607`、`/902`（班级网站）；php 服务经 `127.0.0.1:1181` 直连（与 dev 一致），非 bug。 | `gateway/nginx/conf.d/prj.conf`（全文） | 保持现状（与 dev 对齐）。 | — |

### 2.8 ollama / 数据卷

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| ollama/数据卷 | **class_init 未挂载**：`db/class_init/msg.sql`、`work.sql` 存在，但 base `mysql` 仅挂载 `./db/mysql_init` 到 initdb.d，**未挂载 `./db/class_init`**。prod 的 `prj-php`(902) 依赖 `msg`/`work` 库，若库未建则连接失败。dev/prod 均未自动建这两库，属既有缺口。 | `docker-compose.base.yml:41`（仅 `mysql_init`）<br>`db/class_init/msg.sql`、`work.sql` | **建议**：将 `./db/class_init:/docker-entrypoint-initdb.d/class_init` 加入 base `mysql` 的 volumes（仅首次初始化生效，已存在数据卷不会重跑），或在 dev-mysql 运行时按 docs 运行手册手动建库+授权。本次未改 base（避免首次初始化脚本未知副作用，列为待人工确认）。 | 中 |
| ollama/数据卷 | **macOS 绑定挂载权限**：`./db/redis_data`、`./logs/*` 在 Mac(OrbStack) 下由容器内非 root 用户写入（redis uid 999、nginx、appuser），uid/gid 差异可能导致 redis 写 `dump.rdb` 或日志写入失败（dev 在 Windows 无此问题）。 | `docker-compose.base.yml:88-90`<br>`docker-compose.prod.yml`(prj-redis volumes) | **建议**：确保宿主目录可写；或改用 named volume（如 `redis_data`）规避 uid 漂移。低概率但需在 Mac 首跑时观察日志。 | 低/中 |
| ollama/数据卷 | **base Redis 缺 arm64 清单**：`redis:5.0.14-alpine` 官方镜像缺少 `linux/arm64` 清单，Mac(M2/OrbStack) 拉取失败（prod 文件头已注明）。 | `docker-compose.base.yml:73` | **已修复**：升级为 `redis:7-alpine`（多架构，amd64/arm64 均可用，对 dev(Windows) 向后兼容）。 | 中（已修） |

### 2.9 其他 / 整合精简

| 模块 | 问题描述 | 文件:行号 | 修复方案 | 优先级 |
|------|----------|-----------|----------|--------|
| 整合精简 | **冗余 Redis**：prod 自带 `prj-redis` 与 base `redis` 并存（prod 后端实际只用 `prj-redis`，base `redis` 在 prod 中闲置）。保留 `prj-redis` 是为避免跨 env 改密（见 P15）。 | `docker-compose.prod.yml`(prj-redis)<br>`docker-compose.base.yml`(redis) | **整合方案（待人工确认）**：若要把 prod 统一到 base `redis`，需令 `.env.dev` 与 `.env.backend` 的 `REDIS_PASSWORD` 与 `.env.prod` 的 `REDIS_PASSWORD` 一致（会改动 dev 配置），并移除 `prj-redis`。当前为“不破坏 dev”而保留 `prj-redis` 且仅内网可达。 | 中 |
| 整合精简 | compose 重复段：base 与各 `*dev.yml` 重复定义 `networks: dev-network`、`security_opt`、`pids_limit` 等；可用 YAML anchor 或抽取公共段。 | 多 compose 文件 | 建议后续重构（不在本次 prod 对齐强制范围）。 | 低 |
| 整合精简 | `.env.dev` 冗余键 `MYSQL_ROOT_PWD`（见 2.2）。 | `.env.dev:2-3` | 可清理。 | 低 |
| 整合精简 | base / business-prj 系列 compose 为 **CRLF**（见 P18 / P6）。 | `docker-compose.base.yml` 等 | 建议统一转 LF（Mac 编辑友好、规避个别解析边缘情况）。本次 base 仅改了 Redis 行，未整体转 LF。 | 低 |

---

## 3. ARM64 / macOS 架构适配项（专项）

以下为针对 **Mac mini M2（ARM64 / aarch64）+ OrbStack** 所做的特殊处理：

1. **base Redis 镜像升级 `redis:5.0.14-alpine` → `redis:7-alpine`**（已修，`docker-compose.base.yml:73`）。原因：redis 5 官方镜像缺 `linux/arm64` 清单，Mac 拉取会失败；redis:7-alpine 为多架构镜像，amd64/arm64 通用，对 dev(Windows/amd64) 向后兼容。
2. **所有 prod 服务显式 `platform: linux/arm64`**：`prj-redis`、`prj-backend-c`、`prj-frontend`、`prj-php` 均标注（保留，避免 amd64 模拟退化，保证原生性能）。base 的 `nginx-gateway`/`mysql`/`redis` 所用镜像（nginx:1.25-alpine、mysql:8.0、redis:7-alpine）均为多架构，无需额外 `platform`。
3. **基础镜像架构核验（均 ARM64 兼容 ✓）**：
   - 后端 `eclipse-temurin:17-jdk/jre-alpine` —— 多架构 ✓
   - 前端 `node:18-alpine` / `nginx:1.25-alpine` —— 多架构 ✓
   - PHP `php:8.2-apache` —— 多架构 ✓
   - Ollama(遗留) `ollama/ollama:0.31.2` —— 多架构 ✓
4. **宿主 Ollama 访问统一 `host.docker.internal:11434` + `extra_hosts: host.docker.internal:host-gateway`**：OrbStack 内置解析该 DNS，额外 `extra_hosts` 兜底，与 dev 等效（dev 用 Docker Desktop 同机制）。
5. **数据卷路径 macOS 风格**：本次 prod 未写死任何 Windows 盘符路径（沿用相对路径 `./db/...`、`./logs/...`、`./Niu_Txl` 等），天然跨平台；Mac 上需确保这些相对路径在仓库根目录下可读写（见 P13 权限提示）。
6. **行尾符（CRLF/LF）**：compose / .env 在 Mac 侧应保持 LF；从 Windows 拷贝的 `.env` 须 `sed -i 's/\r$//'`（见 P6）。

---

## 4. 已修改文件清单（绝对路径 + 改动要点）

> 修改前均已在同目录生成 `.aligned.bak-2026-07-20` 备份；未改动任何 dev 文件、未改动 `.env.prod` 真实值。

1. **`D:\crh123dexiaohao\X-box\docker-compose.prod.yml`**（备份：`docker-compose.prod.yml.aligned.bak-2026-07-20`）
   - 移除重复的 `prj-nginx` 服务，统一复用 base 的 `nginx-gateway`。
   - `prj-redis` 移除宿主机端口映射 `127.0.0.1:63790:6379`（消除与 base redis 冲突，仅内网可达）。
   - 全部 prod 业务服务（`prj-backend-c`/`prj-frontend`/`prj-php`/`prj-redis`）统一挂 `dev-network`，删除 `prj-network` 定义。
   - `prj-frontend` 补回 `127.0.0.1:8081:8081`（与 dev 对齐）。
   - `prj-frontend.depends_on` 指向 `nginx-gateway`。
   - 文件头补充分项「对齐说明 + 修复清单」。

2. **`D:\crh123dexiaohao\X-box\docker-compose.base.yml`**（备份：`docker-compose.base.yml.aligned.bak-2026-07-20`）
   - `redis` 镜像 `redis:5.0.14-alpine` → `redis:7-alpine`（ARM64 兼容），并加注释说明。

3. **`D:\crh123dexiaohao\X-box\web\prj-frontend/Dockerfile.prod`**（备份：`Dockerfile.prod.aligned.bak-2026-07-20`）
   - `EXPOSE 80` → `EXPOSE 8081`（与 `nginx.conf` 实际监听端口及网关代理目标一致）。

---

## 5. 待人工确认项（需用户自行填/核）

1. **数据库凭证契约（最关键）**：Mac 部署 prod 时，本机 `.env.dev` 的 `SPRING_DATASOURCE_PASSWORD` 必须与 `.env.prod` 的 `SPRING_DATASOURCE_PASSWORD` **完全相同**（当前两文件均为 `<REDACTED-live-prod>`）。若 Mac 的 `.env.dev` 是该值则 OK；否则后端连不上 `dev-mysql`。
2. **Redis 口令一致性（如选择整合）**：若决定把 prod 统一到 base `redis`（删除 `prj-redis`），需同步 `.env.dev`、`.env.backend`、`.env.prod` 三处的 `REDIS_PASSWORD` 为同一强值，并改 `.env.backend` 的 `REDIS_PASSWORD`（当前为 `redis_default_pass_change_me`）——这会改动 dev 配置，请评估后再做。
3. **class_init 建库**：`msg`/`work` 库未自动挂载初始化（见 P12）。请确认 dev-mysql 已执行 `db/class_init/msg.sql`、`work.sql` 并完成 `class_user` 授权（wrapper 已做 `class_user` 账号+授权迁移，但**库本身**需先存在）。
4. **TLS 证书**：`gateway/nginx/ssl/prj.crt`、`prj.key` 当前仓库内已存在（自签）；生产若需公网证书，请替换这两文件，nginx 443 块无需改。
5. **`.env.prod` 真实密钥**：上线前务必确认 `REDIS_PASSWORD`/`JWT_SECRET`/`DRUID_PASSWORD`/`AI_API_TOKEN` 为强随机值（当前 `.env.prod` 已是真实强值，请勿以 `.env.prod.example` 的 `<openssl ...>` 占位文本直接部署）。
6. **macOS 绑定挂载权限**：首跑请观察 `prj-redis`、各 `logs/*` 容器日志，确认无 “Permission denied” 写入失败；必要时改用 named volume。
7. **`.env` 行尾符**：Mac 侧确认 `.env.prod`/`.env.dev` 为 LF（当前仓库内为 LF，若从 Windows 重新拷贝需转 LF）。

---

## 6. 启动验证建议（macOS / OrbStack）

```bash
# 1) 前置：宿主原生 Ollama 已就绪（监听 0.0.0.0:11434，已 pull bge-m3:latest）
bash scripts/setup-host-ollama.sh --pull-only   # 校验模型
# 本机 .env.dev 的 SPRING_DATASOURCE_PASSWORD 须与 .env.prod 一致（凭证契约）

# 2) 进入项目根目录
cd /path/to/X-box

# 3) 启动（base + prod 合并；必须带 --env-file .env.prod）
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 4) 观察编排正确性（应只有 1 个网关、1 个 mysql、1 个 base redis、1 个 prj-redis、4 个业务服务）
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod ps

# 5) 预期端口（无冲突）：
#    127.0.0.1:80   -> nginx-gateway (base)       统一入口
#    127.0.0.1:443  -> nginx-gateway (base)       HTTPS/TLS
#    127.0.0.1:33060 -> dev-mysql (base)
#    127.0.0.1:63790 -> dev-redis (base)          【仅此一个 63790】
#    127.0.0.1:8081 -> prj-frontend (prod)
#    127.0.0.1:1181 -> prj-php (prod)

# 6) 健康检查 / 日志
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod logs -f prj-backend prj-redis prj-php
curl -k https://127.0.0.1/api/...        # 经网关转发后端
curl -k https://127.0.0.1/               # 前端静态页
curl -fsS http://127.0.0.1:1181/607/gd2.php   # 班级网站探针

# 7) 故障排查要点
#    - 后端连不上 dev-mysql：核对 .env.dev 与 .env.prod 的 SPRING_DATASOURCE_PASSWORD 是否一致。
#    - prj-redis 起不来：确认 .env.prod 的 REDIS_PASSWORD 非空（healthcheck 用其 ping）。
#    - 前端 502：等待 prj-backend-c 健康检查通过（depends_on service_healthy）。
#    - 班级网站 500：确认 msg/work 库已建、class_user 已授权（见第 5 节第 3 条）。
```

---

## 7. 结论

- prod 与 dev 在 **service 命名、网络（dev-network）、端口映射逻辑、依赖服务连接（mysql/redis/ollama 地址）、ARM64 基础镜像** 上现已对齐。
- 直接阻断启动的 **2 处高危端口冲突** 已修复；**网络隔离** 已消除；**base Redis arm64** 已升级。
- 真实密钥/密码零改动；dev 配置零破坏；所有修改均有 `.aligned.bak-2026-07-20` 备份可回滚。
- 剩余中/低级项为整合精简与跨平台运行习惯（CRLF、挂载权限、class_init 建库、compose 去重），已列入待人工确认与后续重构建议。
