# X-box 比对模块重构 · 增量 PRD（简单 PRD / 中文）

> 文档类型：增量 PRD —— 仅描述本轮变更，不含既有稳定功能。
> 范围：代码质量 P1 / 性能 P1 / 测试 P0 / 配置 D2（外部依赖项暂缓）。

## 项目信息
- **语言**：中文
- **技术栈（既有，不替换）**：后端 Spring Boot 3.2.12 (Java 17) + MyBatis；前端 Vue 2.6 + element-ui 2.x；Nginx 网关 + MySQL8 + Redis + 宿主 Ollama bge-m3 向量化 + PHP 班级站。
- **关联文件**：
  - `backend/prj-backend-c/src/main/java/com/prj/controller/CompareController.java`（708 行 God Controller）
  - `web/prj-frontend/src/views/compare/index.vue`、`web/prj-frontend/src/api/compare.js`
  - `.env.dev` / `.env.prod`（及相关的 `.env.backend`）
- **既有事实（已 Read 确认）**：
  - 进度/结果存于 `static final ConcurrentHashMap`（RESULT_CACHE/PROGRESS_CACHE，按 username 键），由单线程守护 `CLEANUP_SCHEDULER` 5 分钟回收。
  - `/compare` 接口**同步**完成 O(origin×new) 暴力余弦匹配（5 万行上限≈25 亿对），阻塞 Tomcat 业务线程。
  - 前端已采用 fire-and-forget + 轮询（1500ms）架构，但 `el-table` 对 `compareResult` **全量渲染无虚拟化/分页**；`startCompare` 仍尝试读取 POST 响应的 list。
  - 源码残留 `[DIAG]` `System.out.println` 调试日志。
  - 全项目**无 `@EnableAsync`/异步线程池配置**；`spring-boot-starter-test` 已引入（JUnit 5 + Mockito 可用）；既有 `CompareControllerCacheTest` 用反射验证缓存回收。

---

## 1. 产品目标（本轮增量）
本轮聚焦消除比对模块"可水平扩展 + 可测试 + 高可用"三大缺口：把 708 行 God Controller 下沉为 Service 分层并抽出可 mock 的 `EmbeddingService` 接口；将 O(origin×new) 的同步暴力比对从 Tomcat 请求线程迁移到异步 worker（`@Async` + 进度回写），解除可用性 DoS 隐患；前端结果表改为虚拟滚动/分页以支撑十万行；补齐核心业务的单元测试（Ollama 容错四种场景）；并核查给出 dev/prod 数据库口令对齐方案。所有变更保持既有"上传→比对→轮询进度→下载"用户流程不变，仅改变内部实现与失败可见性。

---

## 2. 用户故事
> 视角：运营人员（使用方）/ 开发者·测试（交付方）；对比异步化前后体验差异。

- **US-1（运营·前）**：作为运营人员，我提交两个 Excel 后页面一直卡住，后台一比对几十万行时整个系统其它请求也变慢甚至超时（曾 9/9 宕机）。
- **US-1'（运营·后）**：作为运营人员，我点"启动比对"后立刻返回"任务已提交"，弹窗实时显示进度条与当前处理文本；比对在后台跑，我可继续其它操作，完成后结果表流畅浏览/下载，不再卡死系统。
- **US-2（运营·后）**：作为运营人员，当 Ollama 超时或向量化部分失败，我能看到明确的红色错误提示（而非静默产出"全未匹配"退化结果），并可一键重试。
- **US-3（开发/测试·前）**：作为开发者，比对/向量化/余弦/进度缓存全挤在一个 Controller，static Map 无法水平扩展，且因依赖真实 Ollama 完全无法写单元测试（核心业务零测试）。
- **US-3'（开发/测试·后）**：作为开发者/测试，`EmbeddingService` 抽出接口后可用 mock 注入测试超时/连接失败/空响应/部分失败；`CompareService` 编排、`SimilarityService` 纯函数、`ProgressStore` 接口化，每层可独立单测，CI 可跑。

---

## 3. 需求池

### P0（必须，阻断上线）
**P0-1 补 EmbeddingService 容错单元测试（测试）**
- **需求描述**：将 Ollama 调用抽为 `EmbeddingService` 接口 + 实现，并暴露可 mock 的底层 client 缝；新增单元测试覆盖：①正常返回 N 向量（数量一致）；②连接超时（SocketTimeoutException）；③连接失败（IOException/连接拒绝）；④空响应（HTTP 200 但 embeddings 为空）；⑤部分失败（某批次 failCount>0）。测试用 Mockito 模拟底层 client，禁止依赖真实 Ollama。
- **验收标准**：
  - 测试类位于 `src/test/java/com/prj/service/`，使用 JUnit 5 + Mockito。
  - 四类异常场景各自有断言：均抛出明确的 `EmbeddingException`（或等价业务异常，含 cause 分类），调用方可据此向上报"Ollama 超时/连接失败/空响应/部分失败"。
  - `mvn test` 该模块全绿；本次新增用例数 ≥ 5。
  - 既有 `CompareControllerCacheTest` 随 ProgressStore 接口化同步调整或迁移，不被破坏。
- **优先级理由**：诊断指出核心业务零测试、曾 9/9 宕机且不可测；测试是本次"可测"闭环的硬闸门，无测试则无法证明重构不破坏语义。

**P0-2 后端比对异步化（性能/可用性）**
- **需求描述**：`/api/excel/compare` 改为"提交即返回 202"，真正的比对编排交给 `@Async` worker；worker 经 `ProgressStore`（按 username 或 taskId）回写进度/结果；前端轮询 `/progress`、done 后 `/fetchResult`。空文件/类型/大小等同步校验仍在请求线程内立即返回错误。
- **验收标准**：
  - 5 万行上限场景：POST `/compare` 在 1s 内返回 202，Tomcat 业务线程不被 O(origin×new) 计算占用；`/progress` 返回递增 percent，done 后 `/fetchResult` 返回完整结果。
  - 新增 `@EnableAsync`（main 配置或新增 `AsyncConfig`），并配置**有界线程池**（`ThreadPoolTaskExecutor`，核心≤4、队列有界、拒绝策略触发明确告警）；禁止无界默认 `SimpleAsyncTaskExecutor`。
  - 进度/结果存储从 static `ConcurrentHashMap` 迁到 Spring 单例 `ProgressStore` bean（接口化），消除 static 状态。
  - 单用户并发重提：新建任务覆盖/取消旧任务，不残留脏进度。
- **优先级理由**：同步暴力匹配 25 亿对可造成可用性 DoS，是生产事故根因，必须本轮闭合。

### P1（应当，本轮交付）
**P1-1 God Controller 拆分为 Service 分层（代码质量）**
- **需求描述**：CompareController 下沉为：
  - `CompareService`（接口+impl）：比对编排（读取 Excel→调 embedding→调 similarity→组装结果→写 store）；Controller 仅保留 HTTP 边界与文件校验。
  - `EmbeddingService`（接口+impl `OllamaEmbeddingServiceImpl`）：封装 OkHttp 调 Ollama `/api/embed`（input 数组分块，EMBED_BATCH_SIZE=100，connect 30s / read 120s），保留覆盖率阈值（NEW_COVERAGE_THRESHOLD=0.5）与"任一失败即抛"语义。
  - `SimilarityService`（接口+impl 或纯工具）：余弦相似度计算（SIMILARITY_THRESHOLD=0.85 冻结/模糊/未匹配/新增项判定）。
  - `ProgressStore`（接口+内存实现 `InMemoryProgressStore`）：进度/结果存储，保留 5 分钟 TTL 清理；接口为后续 Redis 化留缝。
- **验收标准**：
  - CompareController 行数 ≤ 200（仅 HTTP 映射、文件校验、调度异步、转调 Service）。
  - 现有全部业务常量（阈值、批次大小、超时）与输出字段（name/originVal/matchedName/newVal/similarity/diffType）行为不变（或经测试证明等价）。
  - 移除源码中残留的 `[DIAG]` `System.out.println` 调试日志，改用 `Logger`。
  - 命名遵循既有 `I*Service`/`*ServiceImpl` 约定（参考 `IEmployeeKpiService`）。
- **优先级理由**：708 行 God Controller + static 状态是扩展与测试的双重障碍，是 P0 测试与异步化的前置结构条件。

**P1-2 前端结果表虚拟滚动/分页（性能）**
- **需求描述**：`compare/index.vue` 的 `el-table` 当前全量渲染 `compareResult`（无虚拟化/无分页），十万行卡死。改为虚拟滚动或分页；并调整 `startCompare`：POST 提交后不再依赖其响应 body 的 list（异步返回 202 无 list），结果仅以 `fetchResult` 为准；保留进度弹窗轮询（已存在，1500ms）。
- **验收标准**：
  - 10 万行结果在结果表中滚动/翻页流畅（首屏渲染 < 1s，主线程无长任务卡顿）。
  - 比对提交后前端以轮询 `done → fetchResult` 取数；POST 仅用于即时校验错误（空文件等）展示。
- **优先级理由**：十万行卡死直接拉低运营可用性，是性能 P1 的前端半。

**P1-3 异步失败/超时的用户可见处理（可用性）**
- **需求描述**：worker 异常（Ollama 超时/连接失败/向量化失败/覆盖率不达标）需在 `ProgressStore` 记入失败状态与原因，`/progress` 返回 failed + message，前端弹窗由"成功"转为红色错误提示，并提供"重试"按钮（重新提交）。
- **验收标准**：
  - 任意环节失败：`/progress` 返回 `stage=failed`、`message=明确原因`（如"Ollama 超时，请检查向量服务"）；前端显示错误且不卡在轮询。
  - 提供重试入口；重试创建新任务、清理旧进度。
- **优先级理由**：否则失败后用户只见进度条卡住或静默，无闭环，与 P0 可用性目标相悖。

### P2（可选，时间允许）
- **P2-1 ProgressStore Redis 化**：实现 `RedisProgressStore`，利用既有 Redis（`spring-boot-starter-data-redis` + `RedisConfig`），支持多实例水平扩展；需定义 key 命名、TTL 与序列化。
- **P2-2 结果表差异类型筛选 + 关键字搜索**：提升十万行可读性与定位效率。
- **P2-3 并发任务 taskId 化**：进度/结果改以 taskId 为主键，支持同用户多任务并存与历史查看（当前为单任务 username 键）。

---

## 4. UI 设计稿（异步化后交互流）

```
┌──────────────────────────────────────────────────────────────┐
│ 比对页 (compare/index.vue)                                    │
│  [原始数据 上传框]   [比对数据 上传框]   [启动比对][结果下载]   │
└──────────────────────────────────────────────────────────────┘
        │ 点击"启动比对"
        ▼
┌──────────────────────────────────────────────────────────────┐
│ 进度弹窗 (el-dialog, close-on-click-modal=false)              │
│   当前阶段：向量计算中 / 相似度匹配比对 / 比对任务全部完成     │
│   正在处理文本：批量向量化 1~100 / 50000                       │
│   [==================>---------] 62%                           │
│   30000 / 50000 条                                             │
│   （stage=done → 显示"关闭弹窗"；stage=failed → 红色错误+重试） │
└──────────────────────────────────────────────────────────────┘
        │ 前端 setInterval(1500ms) 轮询 GET /api/excel/progress
        │  ├─ stage != done/failed → 更新进度条，继续轮询
        │  ├─ stage = done → 停止轮询 → GET /api/excel/fetchResult
        │  │                     → 渲染结果表（虚拟滚动/分页）
        │  └─ stage = failed → 停止轮询 → 红色错误提示 + [重试]
        ▼
┌──────────────────────────────────────────────────────────────┐
│ 比对差异结果（虚拟滚动 / 分页 el-table）                       │
│  ┌────────────┬────────────┬────────────┬─────────┐           │
│  │ 原始数据值 │ 新比对数据值│ 匹配结果   │ (筛选)  │           │
│  ├────────────┼────────────┼────────────┤ 完全匹配│           │
│  │ ...        │ ...        │ 语义模糊匹配│ 模糊    │           │
│  │ (仅渲染可视区行，滚动/翻页加载)      │ 未匹配  │           │
│  │                                    │ 新增项  │           │
│  └────────────┴────────────┴────────────┴─────────┘           │
│  [结果下载] 导出全量 Excel                                      │
└──────────────────────────────────────────────────────────────┘

后端时序：
  浏览器 ──POST /compare(文件)──▶ Controller(同步校验) ──202 Accepted──▶ 浏览器
                                          │ @Async 提交
                                          ▼
                                   CompareService.perform()
                                     ├─ EmbeddingService.embed(origin)
                                     ├─ EmbeddingService.embed(new)
                                     ├─ SimilarityService.cosine(...)
                                     └─ ProgressStore.saveResult(username, list)
                                         stage: vector_calc→match_compare→done/failed
  浏览器 ──GET /progress──▶ ProgressStore.getProgress(username) ─▶ JSON
  浏览器 ──GET /fetchResult(done)──▶ ProgressStore.getResult(username) ─▶ list
```

---

## 5. 待确认问题
1. **ProgressStore 本轮是否做 Redis 化？** 诊断列为水平扩展缺口。建议本轮仅抽接口 + 内存实现（P1），Redis 化列为 P2（项目已集成 Redis，改造成本低但需定义序列化/key/TTL）。是否同意先不做？
2. **.env 口令以 dev 还是 prod 为准统一？** 现状：`.env.dev` 的 `PRJ_DB_PWD`/`SPRING_DATASOURCE_PASSWORD` = `Prj@Dev789`；`.env.prod` 两者 = `<REDACTED-live-prod>`，**不一致**。按 `.env.prod` "凭证契约"注释，Mac 共用 dev-mysql 时要求 dev 的 `SPRING_DATASOURCE_PASSWORD` 与 prod 完全相同，否则 prod 后端连不上库。**建议：保持 prod 不动（强随机，不擅改 prod 凭证），把 `.env.dev` 的两个字段改为与 prod 同值（dev←prod）。** 但这是共享库口令变更，且 `.env.dev` 改后若 dev-mysql 已初始化过 prj_user，需确认 `ensure_app_user()` 是否会在重启时把口令同步为新值（否则需重建/ALTER USER）。需用户确认是否按此对齐，以及是否同步更新 `.env.backend`（其 `SPRING_DATASOURCE_PASSWORD` 也是 `Prj@Dev789`）。
3. **前端结果表用 element-ui 自带 virtual-scroll 还是分页？** element-ui 2.x 的 `el-table` **无原生虚拟滚动**；可选：(a) `el-pagination` 分页（最简单、最稳，推荐 P1 基线）；(b) 接入 `vue-virtual-scroller` 虚拟列表（体验更好但引入依赖、需改造列）；(c) 第三方虚拟表格。建议 P1 先分页，P2 再虚拟滚动。请确认走哪条。
4. **异步任务失败/超时的用户可见处理**：重试？部分结果？建议 failed 即全量重试（比对是整体性语义匹配，部分结果意义有限），前端红色提示 + 重试按钮（见 P1-3）。是否接受"失败即重试"而非"返回部分结果"？
5. **进度/结果键选择**：本轮沿用 username 单任务键（重提覆盖），还是引入 taskId 支持多任务并存（P2-3）？建议本轮 username 键即可。
6. **Ollama 容错异常类型约定**：`EmbeddingService` 抛 `EmbeddingException`（含 cause 分类：TIMEOUT / CONNECTION / EMPTY / PARTIAL），便于前端 message 映射。是否同意该异常分类？
