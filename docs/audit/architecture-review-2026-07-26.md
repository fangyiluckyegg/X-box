# X-box 项目系统性审查报告

> 审查人：software-architect（Bob）
> 审查日期：2026-07-26
> 审查范围：`/Users/crh123dexiaohao/X-box`
> 审查方式：静态审查（实际文件 Read + Grep + 配置交叉核验）

## 关于既有报告（重要说明）

仓库内已有一份同日完成的 `docs/verification/comprehensive-review-2026-07-25.md`（覆盖六维度且含修复记录）。本报告在其基础上**独立复核关键项**，并补充既有报告未覆盖的新发现。

- 标注「（既有报告已记录，本次复核确认）」者为已实际验证、属实并已落地；
- 标注「**本次新发现**」者为本次独立审查新增，既有报告未覆盖或描述有误。

> ⚠️ 既有报告多处引用 `Niu_Txl/902/conn.php`，该文件**实际不存在**（真实连接文件位于 `Niu_Txl/902/message/Connections/conn.php` 与 `Niu_Txl/902/work/Connections/conn.php`）。提示：既有报告部分细节未严格基于当前文件树核验，对其全部结论应谨慎采信，建议做一次全文路径复核。

---

## 0. 项目概况与既有处置状态

- **技术栈**：Spring Boot 3.2.12 后端（Java 17，若依风格脚手架改造）、Vue2.6+ElementUI 前端（`web/prj-frontend`）、PHP 8.2 班级网站（`Niu_Txl` 607/902）、MySQL 8 + Redis 7、Nginx 网关、Docker Compose 分层编排（base + business dev/prod）。
- **既有报告结论**：多数历史 P0/P1 已落地；待处理聚焦 R-02（环境隔离）、F-11（Token 存储，已修复）、凭证契约校验、Niu_Txl 整洁度。
- **本次复核确认已落地项**：F-11（HttpOnly+Secure+SameSite=Strict Cookie）、R-12 库名改 `prj_dev`、R-10 表 utf8mb4 迁移脚本（`zz_migrate_utf8mb4.sql`）、dev 弱值轮换、`prod depends_on: service_healthy` + redis/mysql healthcheck、Niu_Txl 整洁（`_notes`/`* copy.php` 已删）、密钥历史泄露已 git-filter-repo 清理、SQL 注入防护（GetSQLValueString）、PHP `password_verify`（bcrypt）。

---

## 1. 项目目录结构与文件组织

整体结构清晰：`backend / web / gateway / db / Niu_Txl / docs / scripts` 职责分明；compose 分层；docs 体系完整（architecture / verification / security / deployment / audit）。**未发现明显结构性问题。**

| 等级 | 问题 | 位置 | 改进建议 |
|---|---|---|---|
| 中 | Niu_Txl 含大量个人二进制资产（照片/视频/Office/实验页），虽已 gitignore 但仍在工作树 | `Niu_Txl/607/Dad's/`、`Niu_Txl/607/永远的607/` | 物理删除或移出仓库（归档到非跟踪目录），降低噪音与误改风险 |
| 低 | 应用名不一致：artifactId=`prj-backend`，但 `spring.application.name: hrmanager`（若依默认名） | `application.yml:35` / `application-prod.yml:18` | 统一为 `prj-*` 系列名，避免监控/链路追踪标识混乱 |
| 低 | 既有报告引用迁移脚本名 `migrate_utf8mb4.sql`，实际文件为 `zz_migrate_utf8mb4.sql`（功能存在） | `db/mysql_init/` | 修正文档引用（功能无碍） |
| 低（**本次新发现**） | 既有报告引用 `Niu_Txl/902/conn.php` 不存在，真实路径为 `.../message/Connections/conn.php` 与 `.../work/Connections/conn.php` | — | 对既有报告全文做一次路径复核，确保结论基于真实文件 |

---

## 2. 依赖关系

服务编排层（compose）依赖方向单向、无循环：`gateway → {frontend, backend, php}`；`backend → mysql/redis`；`php → mysql`。✅

| 等级 | 问题 | 位置 | 改进建议 |
|---|---|---|---|
| 中（**本次新发现**） | Java 应用层存在循环依赖迹象：`spring.main.allow-circular-references: true` 被显式开启，注释称「PageHelper 存在自引用循环」。但 PageHelper 2.1.0 不应有循环引用，更可能是业务代码（service/controller 相互 `@Autowired`）存在循环依赖。既有报告「无循环依赖」结论仅针对服务编排层 | `application.yml:33` / `application-prod.yml:16` | 定位真实循环依赖并重构（提取接口/下沉依赖），移除该开关（会掩盖架构缺陷，Spring 官方不推荐长期使用） |
| 中（既有 R-02，本次复核确认） | prod 复用 base 的 `dev-mysql`/`dev-redis`/`mysql_data` 卷。dev `down -v` 会清空 prod 数据；dev/prod 数据互见；prod 文件非自包含 | `docker-compose.base.yml:25-140`；`docker-compose.prod.yml:28-37` | 短期：卷按环境命名拆分（`dev_mysql_data`/`prod_mysql_data`）；中期：prod 自带独立实例彻底解耦 |
| 低 | `OkHttpOllamaEmbedClient.java:43` 默认 `AI_SERVICE_URL=http://dev-prj-llama:11434`，该服务已从编排移除（迁宿主 Ollama），未显式设会连不上 | `service/embedding/impl/OkHttpOllamaEmbedClient.java:43` | 默认值改为 `http://host.docker.internal:11434`，或 prod 文档标注必填项 |

---

## 3. 代码质量

后端：70 个 Java 文件，分层清晰、命名规范、含单元测试与安全测试（security/compare/similarity 等）。✅
PHP：使用 `GetSQLValueString`（`mysqli_real_escape_string` + `intval`）转义，SQL 注入防护基本到位；`login.php` 用 `password_verify`（bcrypt）。✅

| 等级 | 问题 | 位置 | 改进建议 |
|---|---|---|---|
| 中（**本次新发现**） | **`del-msg.php` 未做访问控制**：文件仅 `session_start()` + `require_once(conn.php)`，直接 `if (isset($_GET['P_ID']))` 即 `DELETE FROM reply/post`。任何未登录用户访问 `del-msg.php?P_ID=xxx` 即可删除任意留言及回复（IDOR / 未授权操作）。`reply-msg.php`、`add-msg.php` 等写操作需逐一核查是否同样缺 session 守卫 | `Niu_Txl/902/message/del-msg.php:1-57` | 在所有管理/写操作 PHP 顶部加 `if (!isset($_SESSION['MM_Username'])) { header('Location: login.php'); exit; }`（参考 login.php 的会话机制） |
| 低 | PHP 仍混用 `mysql_*` 垫片函数（为兼容旧 Dreamweaver 产物） | `Niu_Txl/902/*/Connections/conn.php:25-33` | 长期统一到 mysqli/PDO |
| 低 | 后端密集 `[P0-FIX]`/`[P1-FIX]` 历史修复注释，稳定后建议清理（不影响功能） | 多处 | 稳定后清理冗余注释标记 |

---

## 4. 配置文件

`.env.*`（真实值，gitignore）/`*.example`（模板）双文件拆分到位；真实 `.env` 已被 `.gitignore` + `.dockerignore` 屏蔽。✅
历史密钥泄露已用 git-filter-repo 清理（122 commits），SSH 私钥已从跟踪移除。✅

| 等级 | 问题 | 位置 | 改进建议 |
|---|---|---|---|
| 中（**本次新发现**） | **工作树常驻真实生产密钥**：`.env.prod`（含 MYSQL_ROOT_PASSWORD、PRJ_DB_PWD、REDIS_PASSWORD、CLASS_DB_PWD、ADMIN_INIT_PWD）与 `.env.prod.backend`（含 SPRING_DATASOURCE_PASSWORD、JWT_SECRET、DRUID_PASSWORD、AI_API_TOKEN）均为高熵真实值。虽 gitignore，但明文常驻本地工作树，机器被入侵/误提交即泄露 | `.env.prod` / `.env.prod.backend` | 生产密钥经 CI/CD Secret 或 Vault 注入，工作树仅保留 `.env.prod.example`；或限定 `.env.prod` 仅存在于受控部署机 |
| 中 | `application.yml` 含 dev 默认口令（`Prj@Dev789`、`redis_default_pass_change_me`、JWT 占位串），prod 强制覆盖，误用于 prod 即灾难 | `application.yml:44/68/78` | dev 也建议 fail-fast；或在 `StartupSecurityValidator` 弱值表补全 `change-me-to-a-strong-256-bit-secret-key` |
| 低（既有 R-12，本次复核确认已修复） | `application-prod.yml:21` 默认库名已改 `prj_dev` | `application-prod.yml:21` | 无需处理（已修复） |
| 低 | `init.sql` 硬编码默认管理员 `admin`/密码 `admin123`（BCrypt 哈希），生产若不改是可被猜测的弱凭据 | `db/mysql_init/init.sql:47-48` | 生产初始化从环境变量注入 admin 密码，或 runbook 强制首登改密 |

---

## 5. 安全与性能

已落地：fastjson2 白名单、X-Forwarded-For 覆盖、CSP 头、`StartupSecurityValidator` 弱凭证 prod fail-fast、Druid `/druid/**` 需 ADMIN、prod mysql 端口收敛、F-11 HttpOnly+Secure+SameSite=Strict Cookie。✅

| 等级 | 问题 | 位置 | 改进建议 |
|---|---|---|---|
| 中（**本次新发现**） | **越权删除漏洞**（详见 §3）——当前最明确的可用安全漏洞 | `Niu_Txl/902/message/del-msg.php` | 加 session 鉴权守卫 |
| 中（**本次新发现**） | **网关暴露面与文档矛盾**：`docker-compose.base.yml:20-21` nginx-gateway 监听 `0.0.0.0:80`/`0.0.0.0:443`（2026-07-25 改局域网可达），但 `prj.conf:4` 注释称「严禁 80 端口暴露公网」、`docs/README.md:26` 声称「所有对外端口均绑定 127.0.0.1」。若宿主有公网 IP/DMZ，PHP 站点将公网可达 | `docker-compose.base.yml:20-21`；`docs/README.md:26` | 明确网络边界：局域网用 0.0.0.0 可接受，但必须更新 docs 并在公网主机改回 127.0.0.1 + frp 穿透；评估 `/607/`、`/902/` 是否需认证（目前公开浏览） | **[✅ 已修复 2026-07-26]** 已更新 prj.conf/README 消除矛盾，保留 0.0.0.0 局域网 + frp(1181) 公网路径 |
| 低（**本次新发现**） | **安全注释过时**：`SecurityConfig.java:84-86` 注释「令牌通过 Authorization: Bearer 传递，非 Cookie，CSRF 风险低」已不准确——F-11 已改 HttpOnly Cookie 鉴权。当前 CSRF 由 `SameSite=Strict` 缓解（非漏洞），但注释会误导维护者误以为无 Cookie 而放开相关控制 | `framework/config/SecurityConfig.java:84-86` | 更新注释，或显式补 CSRF 防护（SameSite=Lax + 双重提交 Cookie） | **[✅ 已修复 2026-07-26]** 注释已更新为双模式（优先 Bearer，回退 HttpOnly Cookie Admin-Token）+ SameSite=Strict 缓解 |
| 中（**本次新发现**） | **连接字符集不一致**：`conn.php:22` `mysqli_set_charset($conn,'utf8')`（3 字节），表已迁移 utf8mb4。连接层 utf8 会在传输层截断 4 字节字符（emoji/生僻字） | `Niu_Txl/902/*/Connections/conn.php:22` | 连接层改 `utf8mb4` |
| 低（建议） | Druid `maxActive=20` 偏小（Excel 比对/下载高并发易排队）；Redis 固定 `database=9`，多环境共实例需注意键空间隔离 | `application.yml:50,70` | 按压测上调 `maxActive` 并配 `maxWait`；按环境分库或加 key 前缀 |

---

## 6. 可维护性与可扩展性

整体良好：分层清晰、compose 分层、文档齐全、`docker-entrypoint-wrapper.sh` 幂等自愈（ensure_app_user/ensure_class_user/ensure_root_sha2）。✅

| 等级 | 问题 | 位置 | 改进建议 |
|---|---|---|---|
| 中（既有 R-02） | dev/prod 共享基础设施，环境隔离差，回滚/多环境扩展困难 | compose 编排 | 见 §2 R-02 |
| 中（**本次新发现**） | `allow-circular-references: true` 埋雷，降低长期可维护性 | `application.yml:33` | 见 §2 循环依赖项 |
| 低 | Niu_Txl（旧 PHP）与 Spring Boot 单体并存，技术栈割裂 | `Niu_Txl/` | 长期抽离/重写，降低维护心智负担 |
| 低（既有） | 凭证契约校验脚本 `verify-credential-contract.sh` 存在但为**手动运行**，未集成 CI/启动前置 | `scripts/verify-credential-contract.sh` | 集成进 CI 或 pre-deploy 步骤自动化 |

---

## 7. 优先整改清单（P0 / P1 / P2）

| 优先级 | 项 | 严重度 | 动作 | 位置 |
|---|---|---|---|---|
| **P1** | del-msg.php 越权删除 | 高 | 管理/写操作加 session 鉴权守卫 | `Niu_Txl/902/message/del-msg.php` 等 |
| **P1** | R-02 环境隔离 | 高 | prod 数据卷与 dev 解耦（卷命名拆分）→ 中期独立 prod 实例 | compose base/prod |
| **P1** | 网关暴露面与文档矛盾 | 中 | 明确网络边界、更新 docs、评估 PHP 站点是否需认证 | `docker-compose.base.yml:20-21` / `docs/README.md:26` |
| **P1** | PHP 连接字符集 utf8→utf8mb4 | 中 | `conn.php` `mysqli_set_charset` 改 utf8mb4 | `Niu_Txl/902/*/Connections/conn.php:22` |
| **P1** | 默认 admin/admin123 | 中 | 生产 admin 密码从 env 注入或首登强制改密 | `db/mysql_init/init.sql:47-48` |
| **P2** | 工作树明文生产密钥 | 中 | CI Secret/Vault 注入，工作树仅留模板 | `.env.prod` / `.env.prod.backend` |
| **P2** | Java 循环依赖 | 中 | 定位并重构消除，移除 allow-circular-references | `application.yml:33` / `prod.yml:16` |
| **P2** | 凭证契约校验自动化 | 低 | 集成进 CI/pre-deploy | `scripts/verify-credential-contract.sh` |
| **P2** | SecurityConfig 注释过时 | 低 | 更新 Bearer→Cookie 注释 | `SecurityConfig.java:84-86` |
| **P2** | dev 默认弱值 fail-fast | 低 | 弱值表补全占位串 | `StartupSecurityValidator.java:43` |
| **P2** | 应用名不一致 | 低 | hrmanager → prj-* | `application.yml:35` |
| **P2** | 既有报告路径复核 | 低 | 复核 comprehensive-review 文件引用 | `docs/verification/comprehensive-review-2026-07-25.md` |
| **P2** | 连接池/Redis 库号 | 低 | 上调 maxActive、按环境分库 | `application.yml:50,70` |
| **P2** | Niu_Txl 个人资产清理 | 低 | 物理删除 Dad's/永远的607 等非源码 | `Niu_Txl/607/` |

> 无 P0：历史 P0 已修复，当前最严重项为 P1。

---

## 8. 总结

- 项目安全基线整体良好，历史重大安全问题（密钥泄露、fastjson、X-Forwarded-For、Token 存储 F-11）已响应处置，工程化成熟度较高。
- **本次审查最重要新增发现**：Niu_Txl PHP 模块管理操作缺少访问控制（`del-msg.php` 越权删除）、PHP 连接字符集与表结构不一致（utf8 vs utf8mb4）、网关暴露面与文档矛盾（0.0.0.0 vs 声称 127.0.0.1）。
- 建议优先处理 P1 项（PHP 鉴权、环境隔离、暴露面、字符集、默认 admin），P2 项纳入常态技术债治理。
- 既有同日审查报告整体方向正确，但存在文件路径引用错误与若干遗漏点，建议作为辅助参考而非唯一依据。

*本报告基于静态审查与配置交叉核验，未执行运行时渗透测试。建议对 P1 项优先排期，并对 `del-msg.php` 越权删除做专项运行时验证。*
