# QA 验证与测试补充报告（X-box 全栈）

> 验证人：QA 工程师「严过关（Yan）」
> 验证对象：工程师代码审查报告 `code-review-report.md`（IS_PASS: YES）的 3 处修复 + 高风险维度独立抽查 + 测试补充
> 约束：本机（Windows 代理）无 JDK/Maven/Gradle、离线无 node_modules，**未执行** `mvn`/`npm` 构建或测试；仅做静态核对 + 新增测试文件，测试在 Mac mini 运行。

---

## 0. 结论速览

| 项目 | 结论 |
|------|------|
| 3 处修复正确性/一致性 | ✅ **PASS**（逐项核对一致） |
| 高风险维度独立抽查（6 项） | ✅ 工程师判定**无误判**；F-04(autoType)、F-05(IP伪造) 为真实风险但已被**正确记录**，未谎判为"安全" |
| 是否发现需退回工程师的源码 bug | ❌ **未发现**（详见第四节观察项，均为 Low/已知，非回归、非阻断） |
| 新增/完善测试 | ✅ 后端 2 个 JUnit5 测试类 + 前端 vitest 最小配置与样例 |
| 智能路由判定 | **NoOne**（无源码 bug → 不退回 Engineer；测试由 QA 编写，运行失败由 QA 自修） |

**整体验证结论：PASS。**

---

## 一、3 处修复验证（静态核对）

### F-02 前端 nginx 端口对齐 —— PASS
- `web/prj-frontend/nginx.conf:12` → `listen 8081;`
- 网关 `gateway/nginx/conf.d/prj.conf:40` → `proxy_pass http://$front_host:8081;` 且 `$front_host = prj-frontend`（L39）
- **一致**：前端容器监听 8081，网关 `/` 代理到 `prj-frontend:8081`。✅

### F-03 后端 compose 服务名对齐 —— PASS
- `docker-compose.prod.yml:99` 服务 key `prj-backend-c:`，`:104` `container_name: prj-backend`（保留）
- 网关三处 API 代理（`prj.conf:51 / 65 / 76`）均 `proxy_pass http://$backend_host:8080` 且 `$backend_host = prj-backend-c`
- 后端 env 自洽：`SPRING_DATASOURCE_URL`→`prj-mysql`（L113）、`SPRING_DATA_REDIS_HOST: prj-redis`（L114）、`AI_SERVICE_URL: http://dev-prj-llama:11434`（L118）
- `depends_on: prj-mysql / prj-redis / prj-llama`（L119-122）；`prj-llama` 容器名 `dev-prj-llama`（L81），与 `AI_SERVICE_URL` DNS 解析一致
- **一致**：服务名与网关、各依赖服务名全部对齐。✅

### F-01 CompareController 结果缓存内存泄漏修复 —— PASS
- 写入：`CompareController.java:148` `RESULT_CACHE.put(username, result);`（private static final ConcurrentHashMap）
- 清理：`finally` 清理线程 L161-167 中，在既有 `PROGRESS_CACHE.remove(username)`（L164）**之后**新增 `RESULT_CACHE.remove(username)`（L165）
- key 一致性：`put` 与 `remove` 均使用同一 `String username`（`SecurityUtils.getUsername()`）变量，**类型/语义一致，无漏删/误删**
- 生命周期：与既有 5 分钟进度窗口对齐，超时后再下载提示"无比对结果"属预期行为，无功能回退
- **修复正确且最小改动**：✅

---

## 二、高风险维度独立抽查（防工程师误判）

| 维度 | 抽查文件 | 结论 | 是否误判 |
|------|----------|------|----------|
| SQL 注入 | `EmployeeKpiMapper.xml`、`UserMapper.xml` | 全部 `#{}` 参数化，**无任何 `${}`**；PageHelper orderBy 经 `PageDomain.getOrderBy()` 白名单 | 工程师判"安全"✅ 无误判 |
| 认证/授权 | `SecurityConfig.java`、`LoginUser.java` | `deny-by-default`；`/druid/**` `.hasRole("ADMIN")`(L82)；`LoginUser.getAuthorities` ADMIN→ROLE_ADMIN / 其余→ROLE_USER(L117-126) | 工程师判"安全"✅ 无误判 |
| 敏感信息泄露 | `User.java`、`LoginUser.java` | `User.getPassword()` 标注 `@JSONField(serialize=false)`(L58)+`@JsonIgnore`(L55)；bcrypt 哈希**不**落 Redis | 工程师判"安全"✅ 无误判 |
| CORS | `ResourcesConfig.java` | `setAllowedOrigins(显式白名单)`(L33)，非 `*`；`setAllowCredentials(true)` 兼容 | 工程师判"安全"✅ 无误判 |
| 文件上传 | `UploadServiceImpl.java`、`UploadUtils.java` | Content-Type 首道校验 + **扩展名白名单**(L25/60) + `generateFileName` 随机名(L63)；存储用 `new File(dir, randomName)` 不解析 `..` → 无穿越 | 工程师判"安全"✅ 无误判 |
| 反序列化 | `RedisConfig.java` | 确认 `SupportAutoType`(L91)+`WriteClassName`(L73) → **真实风险**(F-04) | 工程师**已正确记录为风险**，未谎判安全 ✅ |
| IP 伪造 | `IpUtils.java` + `prj.conf` | `getClientIp` 取 XFF **首个** IP(L53-57)；网关 `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`(L43,追加非覆盖) → 伪造首 IP 可绕过 | 工程师**已正确记录为 F-05**，未谎判安全 ✅ |

> 抽查覆盖 7 个维度（超过任务要求的 3–5 个），**未发现工程师将真实风险误判为"安全"的情况**。F-04、F-05 为真实风险但已在报告中显式记录并给出缓解/修复建议，不属于误判，亦不阻断 `IS_PASS=YES`。

---

## 三、新增 / 完善测试清单（仅新增，未改业务源码）

### 后端（JUnit 5，沿用既有 `spring-boot-starter-test` + `spring-security-test`）

| 测试文件 | 路径 | 覆盖点 |
|----------|------|--------|
| `CompareControllerCacheTest` | `backend/prj-backend-c/src/test/java/com/prj/controller/CompareControllerCacheTest.java` | **F-01 修复核心契约**：RESULT_CACHE 为 static final ConcurrentHashMap；写入后模拟清理线程 `remove(username)` 能移除条目；put/remove 同 String key 无误删；1000 用户反复 put/remove 后 size 回落 0（**无无限增长**）；移除缺失 key 为安全 no-op。通过反射访问真实静态实例，不启 Spring、不等 5 分钟定时器。 |
| `TokenServiceSecurityTest` | `backend/prj-backend-c/src/test/java/com/prj/framework/web/service/TokenServiceSecurityTest.java` | **认证维度补强**：合法 token 解析出用户；**篡改签名 token 被拒(null)**；垃圾 token 被拒；缺失 token 被拒；`getUsernameFromToken` 对篡改 token 返回 null（不抛异常）。密钥经 `ReflectionTestUtils` 注入 ≥32 字节强密钥，模拟 prod 严格校验。 |

### 前端（最小可行配置，项目原无测试框架）

| 文件 | 路径 | 说明 |
|------|------|------|
| `vitest.config.js` | `web/prj-frontend/vitest.config.js` | 最小可行 vitest 配置（jsdom 环境，可选启用） |
| `auth.spec.js` | `web/prj-frontend/tests/utils/auth.spec.js` | 针对 F-11 标记的非 HttpOnly Cookie 令牌工具 `src/utils/auth.js` 的写入/读取/清除契约样例 |

> 前端为 Vue2 vue-cli-service 工程，原 `package.json` 无 test 脚本、无 jest/vitest。按任务"给出最小可行测试配置建议（不强制新建整套）"，仅提供 starter 配置 + 1 个样例，未强制接入整套。

### 既有测试确认（已存在且合理，不重复编写）
- `LoginUserTest.java` —— `getAuthorities` 角色映射 5 用例（ADMIN/admin/USER/null/unknown），覆盖完整 ✅
- `RoleBasedAccessTest.java` R4 —— `/druid/**` 匿名/USER→403、ADMIN 越权门槛，覆盖完整 ✅
- `AccountLockServiceTest.java` —— 登录失败计数/锁定/剩余次数，覆盖完整 ✅

---

## 四、观察项 / 已知限制（非源码 bug，供主理人决策）

1. **【Low / 非回归】清理线程按 username 维度计时**：`CompareController` 每次比对在 `finally` 派生一个 5 分钟延时清理线程，`PROGRESS_CACHE`/`RESULT_CACHE` 均按 `username` 移除。若同一用户在前次请求 5 分钟窗口内发起第二次比对，前次定时器到点会移除**第二次**的结果缓存（影响 RESULT_CACHE 与 PROGRESS_CACHE 同等）。此为 F-07 已记录设计的延伸，并非 F-01 引入的回归。可选优化：改共享 `ScheduledExecutorService` 并以 `(username, requestId)` 为键，避免跨请求误清。当前不影响正确性判定。
2. **【Medium / 已记录 F-04】fastjson2 autoType**：`RedisConfig` 仍开启 `SupportAutoType`。当前由 `requirepass` + 内网 + 仅本应用写入缓释。部署后若 Redis 暴露风险上升，建议加 `JSONReader.AutoTypeFilter` 限定仅 `LoginUser`。
3. **【Medium / 已记录 F-05】X-Forwarded-For IP 伪造**：网关 `prj.conf` 用 `$proxy_add_x_forwarded_for`（追加），客户端可伪造首 IP。`IpUtils` 注释声称"避免 IP 伪造"与实际不符。建议单跳内网场景改为 `proxy_set_header X-Forwarded-For $remote_addr;`。
4. **【Low / 已记录 F-11】JWT 非 HttpOnly Cookie + HTTP 明文**：当前无 XSS 注入点（全仓无 `v-html`/`innerHTML`/`document.write`），启用 TLS 后可缓释嗅探。

> 以上均**非本次审查遗漏的源码 bug**，亦非 F-01/F-02/F-03 修复引入。主理人可在部署方案中酌情处理 F-04/F-05/F-11。

---

## 五、预部署检查项（Mac mini）

> 标注【主理人】的项需主理人/运维补齐；其余为工程侧已就绪或可自检项。

- [ ] 【主理人】创建 `X-box/.env.prod`（仓库缺失，F-06 部署阻断）。以 `.env.dev.example` 为模板，填入**真实强随机值**：
  - `MYSQL_ROOT_PASSWORD`（≥16 位随机）
  - `REDIS_PASSWORD`（≥16 位随机）
  - `JWT_SECRET`（**≥32 字节**，否则 jjwt 0.12.x 启动抛 `WeakKeyException` 失败）
  - `DRUID_USERNAME` / `DRUID_PASSWORD`（Druid 控制台 ADMIN 守卫凭证）
  - 可选 `SPRING_DATASOURCE_USERNAME/PASSWORD`（默认 `prj_user`）
- [ ] 【主理人】核对 `application-prod.yml` 的 `${ENV}` 引用（`SPRING_DATASOURCE_PASSWORD`/`DRUID_PASSWORD`/`REDIS_PASSWORD`/`JWT_SECRET`）均能在 `.env.prod` 解析（缺任一即 fail-fast 启动失败）。
- [ ] 【主理人 / 架构】**镜像平台 arm64**：Mac mini 为 arm64。
  - `nginx:1.25-alpine`、`mysql:8.0`、`eclipse-temurin:17`（jdk/jre alpine）均为多架构，可原生 arm64。
  - `ollama/ollama:0.31.2` 原生支持 Apple Silicon arm64 ✅。
  - ⚠️ `redis:5.0.14-alpine` **很可能无 arm64 镜像变体** → 建议**升级为 `redis:7-alpine`**（多架构），并确认 `redis-server --requirepass` 启动参数与 `application-prod.yml` 的 `database: 9` 兼容。
  - 后端/前端为源码构建（`Dockerfile.prod`/`Dockerfile.prod`），compose 在 arm64 主机上默认以 `linux/arm64` 构建；若某基础镜像缺 arm64，构建期即报错，需提前替换。
- [ ] 【主理人】端口绑定：`docker-compose.prod.yml` 已仅绑定 `127.0.0.1:80/443`；**不要**直接公网暴露 80，除非启用 TLS（见下）。
- [ ] 【建议】启用 TLS（C6）：取消 `prj.conf` 预留 443 块注释 + 放置证书 `gateway/nginx/ssl/prj.crt|prj.key` + 取消 HSTS；缓解 F-11 明文令牌嗅探。
- [ ] 【建议】落实 F-05 网关 XFF 覆盖（单跳内网 `$remote_addr`）。
- [ ] 确认 `db/mysql_init/` 初始化脚本存在且 `init.sql`/`migrate_role.sql` 含至少一个 `role='ADMIN'` 真实用户（否则 `/druid` 与写操作无人可访问）。
- [ ] 确认 `db/mysql_data`、`db/redis_data` 持久化卷路径存在（首次启动自动建）。

---

## 六、Mac mini 运行手册（精确命令）

### 0) 前置依赖
```bash
# 后端测试需要
java -version      # 期望 JDK 17
mvn -version       # 期望 Maven 3.9+

# 前端（可选测试）需要
node -v            # >= 15
npm -v             # >= 6
```

### 1) 后端单元测试（JUnit 5）
```bash
cd X-box/backend/prj-backend-c

# 全量测试（含本次新增的 CompareControllerCacheTest / TokenServiceSecurityTest，及既有 13 个测试类）
mvn -q test

# 仅跑本次新增的两个（快速验证 F-01 与 JWT 安全）
mvn -q test -Dtest=CompareControllerCacheTest,TokenServiceSecurityTest
```
> 说明：镜像构建 `Dockerfile.prod` 用 `mvn ... -Dmaven.test.skip=true` 跳过测试；本地验收请用上面的 `mvn test` 跑全量。

### 2) 前端单元测试（vitest，可选）
```bash
cd X-box/web/prj-frontend
npm install                       # 拉取既有依赖
npm install -D vitest jsdom @vue/test-utils   # 安装测试运行器

# 单次运行（CI/验收）
npx vitest run
# 或仅跑样例
npx vitest run tests/utils/auth.spec.js
```

### 3) 整栈生产部署
```bash
cd X-box

# 1) 先创建并编辑 .env.prod（见第五节），再校验编排文件语法
docker compose -f docker-compose.prod.yml --env-file .env.prod config

# 2) 构建并后台启动（--build 重新构建后端/前端镜像，适配 arm64）
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 3) 观察启动与健康检查
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f prj-backend-c
```

### 4) 冒烟测试（网关 127.0.0.1:80）
```bash
# 网关根路径 → 前端静态页（期望 200）
curl -s -o /dev/null -w "GET /            -> %{http_code}\n" http://127.0.0.1/

# 后端可达性：captchaImage 为 permitAll，转发到 prj-backend-c:8080（期望 200）
curl -s -o /dev/null -w "GET /captchaImage -> %{http_code}\n" http://127.0.0.1/captchaImage

# Druid 控制台：匿名应被拒（期望 401 或 403，验证 ADMIN 守卫）
curl -s -o /dev/null -w "GET /druid/       -> %{http_code}\n" http://127.0.0.1/druid/

# 登录获取 token（替换为真实 admin 凭证）
curl -s -X POST http://127.0.0.1/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<admin>","password":"<pwd>"}'

# 比对进度接口需携带 Bearer token（验证认证链路）
curl -s -H "Authorization: Bearer <TOKEN>" http://127.0.0.1/api/excel/progress
```

### 5) 回滚步骤
```bash
cd X-box

# 仅停止服务（保留 db 卷 db/mysql_data、db/redis_data，切勿删除）
docker compose -f docker-compose.prod.yml --env-file .env.prod down

# 回滚后端/前端到上一版：git 切回上一提交后重新构建
git checkout <上一稳定提交>
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build prj-backend-c prj-frontend

# 若仅 .env.prod 配错：修正后直接重启相关服务即可
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d prj-backend-c
```

---

## 七、智能路由判定结论

- **源码有 Bug → Engineer**：❌ 未发现。3 处修复正确一致；高风险维度抽查无误判；观察项均为已知/Low、非回归、非阻断。
- **测试代码有 Bug → QA 自修**：⚠️ 本次新增测试**未在本机执行**（无 JDK），已按既有测试约定静态核对（包路径、import、Mockito `@ExtendWith`/`@InjectMocks`、`ReflectionTestUtils` 用法与 `AccountLockServiceTest` 一致）。若 Mac 上运行失败，由 QA（我）根据报错自修，不退回工程师。
- **无问题 → NoOne**：✅ 当前判定 **NoOne**。验证结论 **PASS**，无需退回工程师，测试补充已完成。

---

> 交付物清单：
> 1. `backend/prj-backend-c/src/test/java/com/prj/controller/CompareControllerCacheTest.java`（新增）
> 2. `backend/prj-backend-c/src/test/java/com/prj/framework/web/service/TokenServiceSecurityTest.java`（新增）
> 3. `web/prj-frontend/vitest.config.js`（新增，可选）
> 4. `web/prj-frontend/tests/utils/auth.spec.js`（新增，可选）
> 5. `docs/verification/qa-verification.md`（本文档）
