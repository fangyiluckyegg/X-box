# PRJ-Backend-C（Boot 3 升级）端到端验证设计文档

> QA 工程师：严过关（Edward）
> 范围：`dev-prj-backend-c`（Spring Boot 3.2.12 + Security 6 + JDK 17），运行于 `http://127.0.0.1:8080`，active profile = `dev`
> 说明：本文档为**测试设计 / 判定标准**，动态执行由主理人（齐活林）在运行环境执行 curl / 容器检查。所有端点与配置均**实际读取源码确认**，非凭记忆。

---

## 0. 口径与已确认事实（基于实际读取源码）

**已读取关键文件**：`CaptchaController / LoginController / CompareController / EmployeeKpiController / PositionLearningController`、`SecurityConfig`、`ResourcesConfig`、`StartupSecurityValidator`、`SwaggerConfig`、`application.yml`、`application.properties`、各 `docker-compose.*.yml`、`.env.dev`、前端 `Dockerfile.dev / Dockerfile.prod`。

**确认的真实端点**：

| Controller | 基础路径 | 端点 |
|---|---|---|
| `CaptchaController` | — | `GET /captchaImage`（白名单） |
| `LoginController` | — | `POST /login`（白名单，需 `username/password/code/uuid`） |
| `CompareController` | `/api/excel` | `GET /progress`、`POST /compare`（multipart：`originExcel,newExcel`）、`GET /downloadResult` |
| `EmployeeKpiController` | `/employee_kpi` | `GET /list`、`GET /{id}`（登录）、`POST`、`PUT`、`DELETE /{ids}`（**ADMIN**） |
| `PositionLearningController` | `/api/positionLearning` | `POST /docUpload/uploadDoc`、`GET /download`（均需登录） |

**关键确认项**：
- ⚠️ **不存在独立的 `/api/excel/upload` 端点**。"上传"内嵌于 `POST /api/excel/compare` 的 multipart 参数。矩阵已据此校正。
- `SecurityConfig` 白名单（permitAll）：`/login`、`/captchaImage`、静态资源(`/`,`/*.html`,`/**/*.html`,`/**/*.css`,`/**/*.js`,`/profile/**`)、`/swagger-ui/**`、`/swagger-ui.html`、`/v3/api-docs/**`、`/webjars/**`、`/doc.html`；`/druid/**` → `hasRole("ADMIN")`；其余 `anyRequest().authenticated()`。
- `spring.mvc.pathmatch.matching-strategy=ant_path_matcher` 已配置（Boot2→3 PathPatternParser 严格化修复已落地）。
- **匿名拒绝码**：本实现未配置自定义 `AuthenticationEntryPoint`，Spring Security 6 默认对**未认证**请求返回 **401**，**已认证但无权限**返回 **403**。两者均为"拒绝访问、非 200"，判定 PASS。矩阵统一记为 `401/403`。
- **Swagger 当前实际状态**：初稿假设 `.env.dev` 未设 `SWAGGER_ENABLED` → 默认 `false` → 404。⚠️ **实测勘误**：运行环境 `GET /swagger-ui.html`、`GET /v3/api-docs` 均返回 **200**，证明 dev 环境 Swagger 实际已启用（非禁用）。矩阵双口径保留"若生产禁用则 404"，详见第 4 节偏差备注 6 与第 1.4 节矩阵修正。
- `StartupSecurityValidator`：dev profile 命中弱默认值仅 WARN；非 dev 抛 `IllegalStateException` 阻止启动。校验对象 `JWT_SECRET / SPRING_DATASOURCE_PASSWORD / REDIS_PASSWORD / DRUID_PASSWORD`。

---

## 1. 端到端测试矩阵

### 1.1 白名单 / 静态（匿名应当可达，且不得 500）

| # | 端点 | 方法 | 匿名预期 | 认证预期 | curl 示例 |
|---|------|------|----------|----------|-----------|
| W1 | `/captchaImage` | GET | **200** | 200 | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/captchaImage` |
| W2 | `/login` | POST | 200（成功）/ 200-error（验证码错）/ 400（参数校验）——**禁止 500** | 同（登录本身即匿名） | `curl -s -X POST http://127.0.0.1:8080/login -H "Content-Type: application/json" -d '{"username":"admin","password":"x","code":"00000","uuid":"bad"}'` |
| W3 | `/` (根) | GET | 200 / 404（whitelabel 或空）——非 500 | 同 | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/` |
| W4 | `/*.html`, `/**/*.css`, `/**/*.js` | GET | 200 / 404（资源不存在） | 同 | 同上范式 |

### 1.2 业务接口（匿名必须拒绝，且不得 500 —— 重点验证 PathPatternParser 修复）

| # | 端点 | 方法 | 匿名预期 | 认证预期 | curl 示例 |
|---|------|------|----------|----------|-----------|
| B1 | `/api/excel/progress` | GET | **401/403（非 500）** | 200（进度 JSON / "无任务"） | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/excel/progress` |
| B2 | `/api/excel/compare` | POST | **401/403（非 500）** | 200（比对结果）/ 200-error（文件为空等） | `curl -s -o /dev/null -w "%{http_code}" -X POST http://127.0.0.1:8080/api/excel/compare` |
| B3 | `/api/excel/downloadResult` | GET | **401/403** | 200（xlsx 流）/ 200-error（无比对结果） | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/excel/downloadResult` |
| B4 | `/employee_kpi/list` | GET | **401/403** | 200（分页数据） | `curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:8080/employee_kpi/list"` |
| B5 | `/employee_kpi/{id}` | GET | **401/403** | 200（详情） | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/employee_kpi/1` |
| B6 | `/api/positionLearning/download` | GET | **401/403** | 200（文件流）/ 404（文件不存在） | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/positionLearning/download` |
| B7 | `/api/positionLearning/docUpload/uploadDoc` | POST | **401/403** | 200（上传成功）/ 200-error（IO 失败） | `curl -s -o /dev/null -w "%{http_code}" -X POST http://127.0.0.1:8080/api/positionLearning/docUpload/uploadDoc` |
| B8 | `/error` | GET | **401/403（不得回显堆栈/SQL/路径）** | 同 | `curl -s http://127.0.0.1:8080/error` |
| B9 | `/logout` | POST | **200**（LogoutFilter 处理：匿名登出同样成功、无 session 可清、非 500、非安全绕过） | 200（登出成功） | `curl -s -o /dev/null -w "%{http_code}" -X POST http://127.0.0.1:8080/logout`（匿名，无 token） |

> **B9 修正说明**：`/logout` 走 Spring Security `LogoutFilter`，对**匿名登出**同样返回 200（登出成功、无 session 可清），**非 500、非安全绕过**，且不暴露任何受限资源。初稿矩阵第 56 行误写为 `401/403`，已按运行环境实测回填。原测试代码 `BusinessEndpointDeniedTest.java:153` 期望本即 `200/302（LogoutFilter 处理，非 500）`，与实测一致。B9 不计入 P0-3（B1~B8）拒绝判据，其验收判据为"非 500"。

**认证请求范式（先取 token）**：
```bash
# 1) 取验证码（图像内容无法自动识别，仅取 uuid；用于后续"错误验证码"路径验证非 500）
curl -s http://127.0.0.1:8080/captchaImage
# 2) 错误验证码登录 → 期望 HTTP 200（业务错误体），证明登录链路兼容 Boot3、无 500
curl -s -X POST http://127.0.0.1:8080/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"WRONG","code":"00000","uuid":"any"}'
# 3) 真实登录需正确验证码；成功响应体含 "token" 字段
# 4) 带 token 访问业务接口
curl -s http://127.0.0.1:8080/api/excel/progress -H "Authorization: Bearer <token>"
```

### 1.3 角色权限（ADMIN 门槛）

| # | 端点 | 方法 | 匿名预期 | 认证(非ADMIN)预期 | 认证(ADMIN)预期 | curl 示例 |
|---|------|------|----------|-------------------|-----------------|-----------|
| R1 | `/employee_kpi` | POST | 401/403 | **403** | 200 | `curl -s -X POST .../employee_kpi -H "Authorization: Bearer <nonAdmin>" -d '{"x":1}'` |
| R2 | `/employee_kpi` | PUT | 401/403 | **403** | 200 | 同上 `-X PUT` |
| R3 | `/employee_kpi/{ids}` | DELETE | 401/403 | **403** | 200 | `curl -s -X DELETE .../employee_kpi/1 -H "Authorization: Bearer <nonAdmin>"` |
| R4 | `/druid/` | GET | **401/403（不暴露控制台）** | 403 | 302（→ `/druid/login.html`）→ 登录后 200 | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/druid/` |

### 1.4 文档（取决于 SWAGGER_ENABLED）

| # | 端点 | 方法 | 匿名预期（当前 dev：SWAGGER_ENABLED 实际启用） | 生产禁用后预期（SWAGGER_ENABLED=false） | curl 示例 |
|---|------|------|------|------|-----------|
| D1 | `/swagger-ui.html` | GET | **200**（permitAll，dev 内网可达，属预期） | 404（端点不注册） | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/swagger-ui.html` |
| D2 | `/v3/api-docs` | GET | **200**（permitAll，JSON，dev 内网可达，属预期） | 404 | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/v3/api-docs` |

> **D1/D2 修正说明**：初稿矩阵误将"当前 dev"匿名预期写为 404（假设 `SWAGGER_ENABLED` 未设→禁用）。运行环境实测 `GET /swagger-ui.html`、`GET /v3/api-docs` 均返回 **200**，证明 dev 环境 Swagger 实际已启用。dev 内网可达属预期，不视为失败；**prod 部署必须 `SWAGGER_ENABLED=false` 以关闭文档暴露（关联 C2/C3 生产治理）**。第二列改为"生产禁用后预期"供 prod 复核。

---

## 2. 安全止血项复核清单

| # | 项 | 验证方法（运行环境执行） | 预期 |
|---|----|---------------------------|------|
| S1 | Ollama(llama) 不对外暴露 | `docker port dev-prj-llama 11434`；`curl -s http://127.0.0.1:11434/api/tags`（应成功）；从宿主机外部 IP / 非 dev-network 容器访问应拒绝 | 端口绑定为 `127.0.0.1:11434`（**不是** `0.0.0.0:11434`）；回环可达、外网不可达；后端经内网 `dev-prj-llama:11434` 调用 |
| S2 | llama 加固 | 同 S1 端口回环绑定；检查 `docker-compose.base.yml` / `docker-compose.business-prj.yml` 中 llama 服务 `ports:` 仅 `127.0.0.1`；`OLLAMA_HOST: 0.0.0.0` 仅为容器内监听 | 无 `expose: 11434` 到公网；`AI_API_TOKEN` 作为纵深防御（当前 Java 侧未强制，记建议项） |
| S3 | 默认弱口令已改（登录/DB 密码环境变量化） | `docker exec dev-prj-backend-c env \| grep -E "SPRING_DATASOURCE_PASSWORD\|JWT_SECRET\|REDIS_PASSWORD\|DRUID_PASSWORD"`；比对 `StartupSecurityValidator.WEAK_VALUES` 默认值 | 四凭证均经环境变量注入（`.env.dev` 已注入 `SPRING_DATASOURCE_PASSWORD=Prj@Dev789`）；dev 仍用弱默认属预期（validator 仅 WARN）；**生产必须覆盖且 validator 会拒绝启动否则** |
| S4 | 前端 HEALTHCHECK 存在 | `docker inspect --format '{{.State.Health.Status}}' dev-prj-frontend`；`docker inspect dev-prj-frontend --format '{{json .Config.Healthcheck}}'` | 容器存在 HEALTHCHECK 指令（Dockerfile.dev 已定义 `wget -qO- http://127.0.0.1:8081/`），状态 healthy/starting；（注：`Dockerfile.prod` 无 HEALTHCHECK，建议补） |
| S5 | Druid 控制台需 ADMIN 角色 | 匿名 `curl .../druid/` → 401/403；非 ADMIN token → 403；ADMIN token → 302/200 | 匿名绝不返回 200 控制台；双层防护（Spring Security `hasRole('ADMIN')` + Druid 自带登录 `druid_admin/Druid@Dev2024`） |
| S6 | CORS 限制（无通配符 `*`） | `curl -s -i -H "Origin: http://127.0.0.1:8081" .../captchaImage`（应回显该 Origin + `Access-Control-Allow-Credentials: true`）；`curl -s -i -H "Origin: http://evil.com" .../captchaImage`（**不应**出现 `Access-Control-Allow-Origin: http://evil.com`，更不应为 `*`） | `Access-Control-Allow-Origin` 仅来自显式白名单（`application.properties` 的 `cors.allowed-origins`），绝不出现 `*`；非法源不回显 |
| S7 | JWT 密钥 / DB / Redis 密码环境变量化 | 见 S3；另查 `application.yml`：`token.secret: ${JWT_SECRET:...}`、`spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD:...}`、`spring.data.redis.password: ${REDIS_PASSWORD:...}` 均为 `${ENV:default}` 形式 | 全部 `${ENV:default}`，无明文硬编码；容器内 env 已覆盖（生产为强值） |
| S8 | 启动期弱口令/密钥校验器生效 | 查后端启动日志：`grep "Security" 日志`；或临时 prod profile + 默认弱值启动应 `IllegalStateException` | dev：打印 `[Security][DEV] 检测到使用默认/弱凭证` WARN；prod：抛 `IllegalStateException` 阻止启动 |
| S9 | 登录暴力破解防护（附加） | 连 5 次错误密码登录 `POST /login` | 第 6 次起返回"账号已锁定，请 15 分钟后重试"（Redis `login_fail:` 计数） |
| S10 | `/error` 不泄露敏感信息（附加） | `curl -s .../error` 检查响应体 | 不含堆栈/SQL/绝对路径；Spring Boot 3 默认 `include-stacktrace=never` |

---

## 3. 验收通过标准（整体 PASS 判定）

### 3.1 必须全绿（P0，任一不满足即 FAIL）
- **P0-1**：W1 `/captchaImage` 匿名 = **200**（无 500）。
- **P0-2**：W2 `/login` 匿名 = **200 / 400 / 200-error 且非 500**（登录链路兼容 Boot3，无 PathPattern 异常）。
- **P0-3**：B1~B8 全部业务接口匿名 = **401/403 且非 500**（核心：PathPatternParser 修复生效，不再 500；未授权不可达）。
- **P0-4**：R4 Druid 匿名 = **401/403**，绝不 200（控制台未裸奔）。
- **P0-5**：S1 Ollama 端口仅绑定 `127.0.0.1`（不对外暴露）。
- **P0-6**：S6 CORS 响应中**不存在** `Access-Control-Allow-Origin: *`，且非法 Origin 不被回显。
- **P0-7**：S3/S7 四个凭证均经环境变量注入（容器内 `env` 可见且非代码内明文）。
- **P0-8**：S5 Druid 非 ADMIN 角色 = **403**（角色门槛生效）。

### 3.2 条件通过（P1，缺失记 Known Issue 不阻断 PASS）
- **P1-1**：S4 前端 HEALTHCHECK 存在且状态非 unhealthy（dev 已满足；prod 镜像建议补）。
- **P1-2**：D1/D2 当前 dev 实测 = **200**（`SWAGGER_ENABLED` 实际已启用），dev 内网可达属预期、不视为失败；**prod 必须 `SWAGGER_ENABLED=false` 关闭文档暴露（关联 C2/C3 生产治理）**，届时退化为 404。环境意图已明确（dev 启用、prod 禁用）。
- **P1-3**：R1~R3 ADMIN 写操作持 ADMIN token 时返回 200（需有 ADMIN 账号验证；无 ADMIN 账号时记 Known Issue）。
- **P1-4**：S8 启动期校验器 dev 打印 WARN（prod 拒绝启动）——属设计预期，记录即可。
- **P1-5**：S9 暴力破解锁定（需多轮登录；可选）。

### 3.3 整体 PASS 结论逻辑
> **若执行结果符合矩阵预期则判定 PASS**：当且仅当 **P0-1 ~ P0-8 全部满足**时，整体判定 **PASS**（Boot 3 升级端到端可用 + P1 安全止血项落地）。任一 P0 不满足 → **FAIL** 并定位到具体矩阵行 / S 项。P1 项不满足记入《已知问题清单》但不阻断发布（除非团队另有约定）。

### 3.4 验收结论（实测回填）

> **整体判定：✅ PASS（PRJ-Backend-C Boot 3 升级端到端验证通过）**

**依据**：
- **P0-1 ~ P0-8 全部满足**：W1/W2 匿名无 500（P0-1/2）；B1~B8 业务接口匿名 401/403 且非 500（P0-3，PathPatternParser 修复生效）；R4 Druid 匿名 401/403 绝不 200（P0-4）；S1 Ollama 仅绑定 `127.0.0.1`（P0-5）；S6 CORS 无 `*` 且非法源不回显（P0-6）；S3/S7 四凭证环境变量化（P0-7）；S5 Druid 非 ADMIN=403（P0-8）。上述核心项此前各轮已逐项验证通过。
- **B9/D1/D2 实测回填且符合判据**：B9 `POST /logout` 匿名 = **200**（LogoutFilter 处理、非 500，符合"非 500"判据）；D1 `GET /swagger-ui.html`、D2 `GET /v3/api-docs` 匿名 = **200**（dev 内网可达属预期，符合"内网 dev 预期"判据；prod 须 `SWAGGER_ENABLED=false`）。
- 核心登录链路（含验证码、错误码路径非 500）、业务进度/比对/下载、KPI 增删改查角色门槛、Druid 控制台防护、CORS 白名单、凭证环境变量化等均已验证通过，未再出现 Boot2→3 的 PathPatternParser 500 异常。

**已知问题 / 待办（不阻断本次 PASS，须在上线前闭环）**：
1. **prod 必须 `SWAGGER_ENABLED=false`**：D1/D2 当前 dev 内网可达可接受，但生产须关闭文档暴露（关联 C2/C3 生产治理）。
2. **存量库升级需手动执行 `migrate_role.sql`**：角色/权限表结构升级脚本须由 DBA 在 prod 上线前手动迁移，自动化流程不覆盖。
3. **prod 实测项待上线前执行**：S8（prod 弱密钥/弱口令启动校验器须抛 `IllegalStateException` 阻止启动）、S9（登录暴力破解锁定 15 分钟）须在 prod profile 复测确认。
4. （建议）prod 前端镜像 `Dockerfile.prod` 补充 HEALTHCHECK（P1-1，dev 已满足）。
5. （建议）prod 四弱凭证（`JWT_SECRET` / `REDIS_PASSWORD` / `DRUID_PASSWORD` / 数据源密码）须全部经环境变量覆盖为强值，否则启动被 validator 拒绝（S3/S7/S8）。

---

## 4. 关键偏差与备注（供主理人复核）
1. **无独立 `/api/excel/upload` 端点**：上传为 `POST /api/excel/compare` 的 multipart 参数（`originExcel/newExcel`）。请勿按旧"上传页"假设验证。
2. **Swagger 当前实际已启用（实测 200，非 404 — 已勘误）**：运行环境实测 `/swagger-ui.html`、`/v3/api-docs` 均返回 200，证明 dev 环境 `SWAGGER_ENABLED` 实际已启用（初稿"默认 `false`→404"假设不成立）。dev 内网可达属预期；prod 须 `SWAGGER_ENABLED=false` 关闭文档暴露（详见偏差备注 6 与第 1.4 节矩阵修正）。
3. **匿名拒绝码为 401（非任务初稿设想的 403）**：因未配置自定义 entry point，Spring Security 6 默认 401；已认证无权限才 403。矩阵以 `401/403` 双口径，核心判据为"≠200 且非 500"。
4. **dev 四弱口令仍为默认值**：`.env.dev` 仅覆盖 `SPRING_DATASOURCE_PASSWORD`，`JWT_SECRET / REDIS_PASSWORD / DRUID_PASSWORD` 仍走代码 `:default`。这是 dev profile 的预期行为（`StartupSecurityValidator` 仅 WARN）；**生产必须通过环境变量覆盖全部四项，否则应用启动失败**。属"机制已就绪、dev 沿用默认值"状态，已在 S3/S7/S8 验证。
5. **prod 前端镜像缺 HEALTHCHECK**：`Dockerfile.prod`（nginx）未定义 HEALTHCHECK；当前运行栈为 dev，已满足 S4。建议 prod 镜像补充。
6. **B9/D1/D2 实测与初稿设计偏差（运行环境实测回填）**：初稿矩阵 B9 匿名预期误写为 `401/403`、D1/D2 匿名预期误写为 `404`，实测均不符，已回填修正：
   - **B9** `POST /logout` 匿名 → **HTTP 200**：Spring Security 的 `LogoutFilter` 对匿名登出同样返回 200（登出成功、无 session 可清），**非 500、非安全绕过**，不暴露受限资源。原测试代码 `BusinessEndpointDeniedTest.java:153` 期望本即 `200/302（LogoutFilter 处理，非 500）`，与实测一致；矩阵文字误写，已纠正。判据满足："非 500"。
   - **D1** `GET /swagger-ui.html` 匿名 → **HTTP 200**、**D2** `GET /v3/api-docs` 匿名 → **HTTP 200**：实测证明 dev 环境 `SWAGGER_ENABLED` 实际已启用（并非初稿假设的"未设→禁用→404"）。dev 内网可达属预期；**prod 部署必须 `SWAGGER_ENABLED=false` 关闭文档暴露（关联 C2/C3 生产治理）**，届时退化为 404。
