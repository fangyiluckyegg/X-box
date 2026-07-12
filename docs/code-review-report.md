# X-box 全栈代码审查报告

> 审查人：软件工程师（Kou / 寇豆码）
> 审查范围：前端 `web/prj-frontend/src`（41 文件）、后端 `backend/prj-backend-c/src`（66 个 .java + 配置 + Mapper XML）、AI 推理脚本、Docker / Nginx / 部署编排、参考文档（项目开发说明、ai_llama/README-SECURITY.md、docs/archive/2026-07-code-audit 既有修复方案）。
> 约束：本机无 JDK/Maven，仅做**源码级静态审查 + 就地编辑修复**；不编译、不 `npm install` / `mvn`；所有改动均为外科手术式精准修改，未削弱既有安全姿态（IP 锁、CORS 过滤器、安全响应头、prod 严格密钥校验等）。

---

## 1. 概览

| 指标 | 数值 |
|------|------|
| 审查源文件数（估算） | 前端 ~41、后端 ~66 个 .java + 12 个资源配置 + 2 个 Mapper XML + 3 个 application 配置 |
| 发现总数 | **15** |
| Critical | 2（均已修复：部署集成 A/B） |
| High | 2（F-01 已修复；F-06 仅记录，部署阻断项） |
| Medium | 2（F-04、F-05，仅记录） |
| Low | 6（F-07~F-12，仅记录） |
| Info | 3（F-13~F-15，信息性） |
| **已就地修复** | **3 项**（F-01 内存泄漏、F-02 前端端口、F-03 后端服务名） |
| **仅记录未改** | **12 项** |

---

## 2. 发现清单（表格）

| ID | 文件:行号 | 严重度 | 类别 | 问题描述 | 修复动作 | 备注 |
|----|-----------|--------|------|----------|----------|------|
| F-01 | `backend/.../controller/CompareController.java:47,148,156-164` | High | 内存泄漏 | 静态 `RESULT_CACHE`（按用户名缓存比对结果）每次成功比对写入，但**永不回收**；仅 `PROGRESS_CACHE` 被清理 → JVM 生命周期内无限增长 | **已修复** | 在既有 5 分钟清理线程中一并 `RESULT_CACHE.remove(username)`，生命周期与进度窗口对齐 |
| F-02 | `web/prj-frontend/nginx.conf:12` | Critical | 部署集成(端口) | 前端 nginx `listen 80;`，但网关 `gateway/nginx/conf.d/prj.conf` 将 `/` 代理到 `prj-frontend:8081` → 前端 80 端口与生产/开发网关不可达 | **已修复** | `listen 80;` → `listen 8081;` |
| F-03 | `docker-compose.prod.yml:99` | Critical | 部署集成(服务名) | prod compose 后端**服务 key** 为 `prj-backend`，但网关按 `prj-backend-c:8080` 解析；Docker 内网 DNS 按服务名解析，`prj-backend-c` 在生产栈不存在 → 所有 API 502 | **已修复** | 服务 key 改为 `prj-backend-c`，**保留** `container_name: prj-backend`（同步 dev compose 命名） |
| F-04 | `.../framework/config/RedisConfig.java:90-91` | Medium | 安全(反序列化) | `FastJson2RedisSerializer` 反序列化启用 `JSONReader.Feature.SupportAutoType` + `WriteClassName`，等价于开启 fastjson autoType；若 Redis 被写入恶意 `@type`，可触发反序列化 gadget 链（RCE） | 仅记录 | 当前由 Redis `requirepass` + 内网 + 仅本应用写入缓释；建议改为**类型白名单**或关闭 autoType（受“零新增依赖”约束，未盲改） |
| F-05 | `.../common/utils/IpUtils.java:40-58` + `gateway/nginx/conf.d/prj.conf:43,55,68,80` | Medium | 安全(IP 伪造) | `getClientIp` 取 `X-Forwarded-For` **首个** IP；但 nginx 用 `$proxy_add_x_forwarded_for`（追加而非覆盖），客户端可伪造首个 IP → 绕过验证码频限 / 登录 IP 维度锁定。代码注释声称“防 IP 伪造”，与实际不符 | 仅记录 | 建议网关将 `proxy_set_header X-Forwarded-For $remote_addr;`（单跳内网覆盖） |
| F-06 | `docker-compose.prod.yml:100,36,...` | High | 部署阻断 | `env_file: .env.prod` 引用，**仓库中 `.env.prod` 不存在**（仅有 `.env.dev.example`）→ `docker compose --env-file .env.prod` 直接失败，prod 无法启动；且 `application-prod.yml` 四凭证均为 `${ENV}` 无默认值，缺失即启动失败 | 仅记录 | **需主理人在部署方案中处理**：依 `.env.dev.example` 创建 `.env.prod` 并填入真实强随机密钥（JWT_SECRET≥32B、SPRING_DATASOURCE_PASSWORD、REDIS_PASSWORD、DRUID_PASSWORD/DRUID_USERNAME） |
| F-07 | `CompareController.java:158-163` | Low | 并发/资源 | 每次比对请求 `new Thread(...).start()` 一个非守护线程做 5 分钟延时清理；高并发下线程累积（受请求速率约束，单线程存活≤5min） | 仅记录 | 建议改用共享 `ScheduledExecutorService`；本次为控制改动面，未改 |
| F-08 | `CompareController.java:243-266,295-312` | Low | 边界/DoS | Excel 比对读取无行数/单元格上限保护，`WorkbookFactory.create(file.getInputStream())` 对恶意/超大 xlsx（zip bomb）可耗尽内存；仅受 `spring.servlet.multipart.max-file-size=200MB` 限制，压缩比可放大 | 仅记录 | 如有大文件场景，建议加行数/单元格上限流式保护 |
| F-09 | `.../service/impl/UploadServiceImpl.java:48` | Low | 安全(纵深) | 第一道校验用客户端可控的 `file.getContentType()`（易伪造）判断 allowType | 仅记录 | 已被**扩展名白名单 + 随机文件名**二次兜底，无法 RCE/路径穿越；属纵深加固项，非必须改 |
| F-10 | `CompareController.java:90` | Low | 配置一致性 | Ollama 向量地址硬编码 `http://dev-prj-llama:11434/api/embeddings`，未使用 `AI_SERVICE_URL` 环境变量 | 仅记录 | 生产栈 `AI_SERVICE_URL` 亦指向 `dev-prj-llama:11434`（container_name 解析可用），功能正常；建议后续统一走 env 以便一致性 |
| F-11 | `web/.../utils/auth.js:6-9`、`request.js:20` | Low | 安全(令牌) | JWT 存于 **非 HttpOnly** Cookie（`js-cookie` 无法设 httpOnly）；`secure` 仅在 HTTPS 下生效 → 生产 HTTP 明文传输可被嗅探 | 仅记录 | 当前未发现 XSS 注入点（全仓无 `v-html`/`innerHTML`/`document.write`），风险较低；启用 TLS（C6）后可缓释嗅探 |
| F-12 | `CompareController.java:217` | Low | 正确性 | 下载响应 `Content-Disposition` 文件名 `比对结果.xlsx` 为原始非 ASCII，未做 RFC5987 编码，个别旧浏览器文件名乱码 | 仅记录 | 现代浏览器已兼容，影响极小 |
| F-13 | `.../common/core/page/PageDomain.java:87-96` | Info | 正确性 | `setIsAsc` 将非 `"ascending"` 一律置 `"desc"`（含 `"ASC"` 等），但 `getOrderBy()` 二次校验仅接受 asc/desc，最终安全 | 仅记录 | 无害；前端未传 isAsc，默认 asc |
| F-14 | `.../framework/web/service/TokenService.java:106-114` | Info | 设计 | 每次已认证请求都触发 `verifyToken` → 滑动续期，活跃会话令牌不过期 | 仅记录 | 设计取舍，非缺陷 |
| F-15 | `backend/prj-backend-c/src/ai-infer/` | Info | 完整性 | AI 推理 Python 脚本在仓库中**不存在**（仅有 `321.md` 说明）；实际推理由 Java `CompareController` 直接 HTTP 调用 Ollama 完成 | 仅记录 | 不影响本次审查；如依赖 Python 脚本需补齐 |

---

## 3. 已修复项逐条说明

### F-02 — 前端 nginx 端口对齐（部署集成 A）
- **文件**：`web/prj-frontend/nginx.conf`
- **改动**：`server { listen 80; ... }` → `listen 8081;`
- **原因**：网关 `prj.conf` 将 `/` 代理到 `prj-frontend:8081`，原 `listen 80` 使前端容器端口与网关期望不一致，开发/生产均 404。改后前端在 8081 监听，网关可正常转发。

### F-03 — 后端 compose 服务名对齐（部署集成 B）
- **文件**：`docker-compose.prod.yml`
- **改动**：服务 key `prj-backend:` → `prj-backend-c:`，**保持不变** `container_name: prj-backend`。
- **原因**：网关按服务名 `prj-backend-c:8080` 代理 API；原 key `prj-backend` 在生产栈 DNS 中不存在，导致所有 API 502。改后服务名与网关一致。已核对：后端 env 中 `SPRING_DATASOURCE_URL` 用 `prj-mysql`、`SPRING_DATA_REDIS_HOST: prj-redis`、`AI_SERVICE_URL: http://dev-prj-llama:11434` 均指向存在的服务名/容器名；`depends_on` 引用的 `prj-mysql/prj-redis/prj-llama` 服务名也未受影响；`prj-llama` 服务 key 保持、container_name `dev-prj-llama` 保持，与 AI_SERVICE_URL 解析一致。未改动 dev 相关 compose（base / business-prj.dev）。

### F-01 — CompareController 结果缓存内存泄漏修复（代码缺陷）
- **文件**：`backend/prj-backend-c/src/main/java/com/prj/controller/CompareController.java`
- **改动**：在 `compareExcel` 的 `finally` 清理线程中，原有仅 `PROGRESS_CACHE.remove(username)` 之后，**新增** `RESULT_CACHE.remove(username)`。
- **原因**：`RESULT_CACHE`（静态 `ConcurrentHashMap`，按用户名缓存比对结果）在每次成功比对时 `put`（原第 148 行），但全代码无任何回收路径；`PROGRESS_CACHE` 有 5 分钟延时清理线程，而 `RESULT_CACHE` 永不清理 → 随比对次数无限增长，属于确定性内存泄漏。修复将其生命周期与既有 5 分钟进度窗口对齐（超时后再下载会提示“无比对结果”，属预期行为），无功能回退，且改动与既有清理线程同处，改动面最小、可编译一致。

---

## 4. 仅记录未改的高风险项（含建议）

1. **F-04 fastjson2 autoType（Medium）**：`RedisConfig` 的反序列化开启 `SupportAutoType`。若 Redis 曾处于无认证窗口或被攻陷，恶意 `@type` 可触发 gadget 链。建议：使用 fastjson2 的 `JSONReader.AutoTypeFilter` 限定仅允许 `com.prj.common.core.domain.model.LoginUser`（或关闭 autoType，因缓存仅存 LoginUser 一类对象）。受“零新增依赖”约束，本次未盲改。
2. **F-05 X-Forwarded-For IP 伪造（Medium）**：`IpUtils.getClientIp` 取 XFF 首个 IP，而网关用 `$proxy_add_x_forwarded_for` 追加，**未覆盖**客户端伪造值 → 可绕过验证码频限与登录 IP 锁定。建议网关对单跳内网场景改为 `proxy_set_header X-Forwarded-For $remote_addr;`。属网关配置决策，未越权改动。
3. **F-06 `.env.prod` 缺失（High，部署阻断）**：见第 6 节“需主理人处理”。
4. **F-07 每请求派生线程（Low）**：建议收敛为共享 `ScheduledExecutorService`。
5. **F-08 Excel 读取无行数上限（Low）**：建议对超大/压缩文件加行数保护。
6. **F-09 上传首道校验依赖客户端 Content-Type（Low）**：已被扩展名白名单 + 随机文件名兜底，无实际利用路径。
7. **F-10 Ollama 地址硬编码（Low）**：功能正常，建议统一走 `AI_SERVICE_URL` env。
8. **F-11 JWT 非 HttpOnly Cookie + HTTP 明文（Low）**：当前无 XSS 注入点；启用 TLS 后可缓释嗅探。
9. **F-12 下载文件名非 ASCII 未 RFC5987 编码（Low）**：现代浏览器兼容。

---

## 5. 显式验证为安全/正确的关键项（本次审查确认无问题）

- **SQL 注入**：所有 Mapper XML 均使用 `#{}` 参数化；`PageHelper.startPage(pageNum, pageSize, orderBy)` 的 `orderBy` 经 `PageDomain.getOrderBy()` 严格白名单校验（列名 `[a-zA-Z0-9_]+` + 方向仅 `asc/desc`），无 `${}` 拼接注入面。✅
- **认证/授权**：`SecurityConfig` 默认拒绝（deny-by-default）；写操作与 `/druid/**` 均 `@PreAuthorize("hasRole('ADMIN')")`；`GlobalExceptionHandler` 对 `AccessDeniedException` 返回 403；`LoginUser.getAuthorities()` 据 `user.role` 正确映射 `ROLE_ADMIN/ROLE_USER`（重度修复方案中 C1 已落地）；`StartupSecurityValidator` 在 prod/无 profile 下对弱凭证 fail-fast 阻止启动。✅
- **敏感信息泄露**：`User.password` 标注 `@JSONField(serialize=false)`，`LoginUser.getPassword()` 同样不序列化，bcrypt 哈希不落 Redis；`GlobalExceptionHandler` 不向客户端暴露异常堆栈/详情。✅
- **异常处理**：`CompareController.downloadResult` 在 `finally` 中分别关闭 `OutputStream` 与 `Workbook` 并捕获各自 IOException；前端 axios 请求/响应拦截器均 `Promise.reject`，无静默吞异常。✅
- **CORS**：`ResourcesConfig` 用显式 origins 白名单（非 `*`），`setAllowCredentials(true)` 与之兼容。✅
- **文件上传**：扩展名白名单 + `UploadUtils.generateFileName` 生成随机文件名，`new File(parent, randomName)` 不会解析路径中的 `..` → 无路径穿越、无 RCE。✅
- **依赖安全**：`pom.xml` 版本均现代且已打 CVE 补丁（fastjson2 2.0.53、okhttp 4.12.0、poi 5.2.3、log4j 2.23.1、commons-io 2.16.1、jjwt 0.12.6、mysql-connector-j 8.0.33、druid-spring-boot-3-starter 1.2.23）。✅
- **编译一致性（静态核对）**：`IdUtils.fastUUID()` → `UUID.fastUUID()` 存在；`CaptchaController` 用的 `Base64.encode(byte[])` 存在；`ServiceException.getCode()`、`WeakCredentialException extends IllegalStateException`（与 `StartupValidatorTest` 的 `assertThrows(IllegalStateException.class)` 一致）、`TableDataInfo.getTotal/getRows` 均齐备；`SpringBootApp`、`ApplicationConfig(@MapperScan)` 正常。✅

---

## 6. 需主理人在部署方案中处理的事项（未改动部署外配置）

1. **【High / 阻断】创建 `.env.prod`**：`docker-compose.prod.yml` 通过 `env_file: .env.prod` 引用，但仓库中该文件不存在（仅有 `.env.dev.example`），且 `application-prod.yml` 四凭证均为 `${ENV}` 无默认 → 缺失即启动失败。请依 `.env.dev.example` 创建 `.env.prod` 并填入真实强随机值：`JWT_SECRET`（≥32 字节）、`SPRING_DATASOURCE_PASSWORD`、`REDIS_PASSWORD`、`DRUID_PASSWORD`（及 `DRUID_USERNAME`）。
2. **【建议】网关 X-Forwarded-For 覆盖**（F-05）：单跳内网场景将 `proxy_set_header X-Forwarded-For $remote_addr;` 以真正防 IP 伪造。
3. **【建议】fastjson2 autoType 收敛**（F-04）：如后续可放宽“零新增依赖”，为 `RedisConfig` 增加反序列化类型白名单。
4. **【建议】生产启用 TLS**（C6）：当前 `prj.conf` 的 443/TLS 块为注释态，令牌以明文 Cookie 经 HTTP 传输；如对外暴露务必启用证书 + HSTS。

---

## 7. 结论

### IS_PASS: **YES**

- 改动（F-02 / F-03 / F-01）均为精准、最小、内部自洽的就地编辑，未改动代码风格、未削弱安全姿态、未做大段重构；改动前后服务名/端口/缓存键一致性已逐项核对，不存在会破坏编译或导致既有契约断裂的编辑。
- 所有“不确定 / 需权衡”的项（F-04~F-15）均**仅记录、未盲改**；其中唯一会阻断生产的 `.env.prod` 缺失（F-06）已在第 6 节标注“需主理人处理”，不影响代码层面 `IS_PASS=YES` 的判定。
- 两个主理人已定位的部署集成问题（A 前端端口 / B 后端服务名）已实际修改文件并验证一致性。

> 一句话：代码层面 3 项已修（含 2 个部署阻断 + 1 个内存泄漏），其余 12 项风险已记录；请主理人在部署方案里补齐 `.env.prod` 并酌情处理 F-04/F-05/F-11 三项安全加固。
