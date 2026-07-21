# X-box 后端 Java P1b 加固变更清单

> 执行人：软件工程师 寇豆码（Kou）　|　日期：2026-07-14
> 范围：后端 `prj-backend-c` 三项安全/稳定性加固（SEC5 / P3 / P4）
> 设计输入：`docs/audit/archive/2026-07-14/docs/X-box-optimization-report-2026-07-14.md` 第 86/99/100/132/193-215 行
> 原则：最小变更、不破坏业务、不新建无关文件、改动可逆、未提交 git。

---

## 一、SEC5 — RedisConfig 去除 fastjson2 autoType（RCE 防护）

### Redis 实际存储类型枚举（关键前置）

全局 `src/main/java` 中写入 Redis 的路径仅有 `RedisCache.setCacheObject` → `redisTemplate.opsForValue().set(...)` 一条；枚举所有调用点得到实际存入的 value 类型共 **3 类**：

| 类型 | 全限定名 | 调用点 |
|------|----------|--------|
| 登录态对象 | `com.prj.common.core.domain.model.LoginUser` | `framework/web/service/TokenService.java:145` |
| 验证码 / 频率标记 | `java.lang.String` | `controller/CaptchaController.java:89`、`CaptchaController.java:91` |
| 登录失败计数 | `java.lang.Integer` | `framework/web/service/AccountLockService.java:87`、`AccountLockService.java:106` |

> 无 hash / list / stream / pub-sub 等其他写入路径（`opsForHash`/`boundHashOps`/`convertAndSend` 等检索为空）。
> 因此白名单必须包含上述三类，否则运行期反序列化会抛异常。白名单内均为应用自有类型或 JDK 不可变类型，无可利用的 gadget 链。

### 变更明细

| 文件 | 行 | 原片段 | 新片段 | 影响 |
|------|----|--------|--------|------|
| `framework/config/RedisConfig.java` | 27 / 29-34（类注释） | `SupportAutoType 还原真实类型` | `反序列化采用<b>类型白名单</b>还原真实类型` + `[SEC5] 已去除 SupportAutoType…改为 autoTypeFilter 白名单` | 类注释说明去 autoType |
| `framework/config/RedisConfig.java` | 60-64（序列化器注释） | 未说明白名单 | 注明反序列化通过类型白名单还原、不开启 SupportAutoType | 注释 |
| `framework/config/RedisConfig.java` | 69-82（新增字段） | 无 | `private static final JSONReader.AutoTypeFilter AUTO_TYPE_WHITELIST = JSONReader.autoTypeFilter("com.prj.common.core.domain.model.LoginUser","java.lang.String","java.lang.Integer");` | 类型白名单（含枚举出的 3 类） |
| `framework/config/RedisConfig.java` | 90-91（deserialize 核心） | `return JSON.parseObject(new String(bytes, DEFAULT_CHARSET), Object.class, JSONReader.Feature.SupportAutoType);` | `return JSON.parseObject(bytes, Object.class, AUTO_TYPE_WHITELIST);` | **去除 autoType，改白名单还原，防 @type 触发 RCE** |

> `serialize` 的 `JSONWriter.Feature.WriteClassName` 保持不变（白名单生效时 `@type` 仅对白名单类还原，无害）。
> `JSONReader` 已在文件顶部 import，`JSONReader.AutoTypeFilter` 为内部类，无需新增 import。

---

## 二、P3 — CompareController 共享线程池替代每请求线程（稳定性）

| 文件 | 行 | 原片段 | 新片段 | 影响 |
|------|----|--------|--------|------|
| `controller/CompareController.java` | 31-34（import） | 仅 `import java.util.concurrent.TimeUnit;` / `ConcurrentHashMap` / `atomic.AtomicInteger` | 新增 `import java.util.concurrent.Executors;` `import java.util.concurrent.ScheduledExecutorService;` | 支持共享调度器 |
| `controller/CompareController.java` | 77-87（新增字段） | 无 | `private static final ScheduledExecutorService CLEANUP_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "compare-cache-cleaner"); t.setDaemon(true); return t; });` | 单例、守护线程、共享调度池 |
| `controller/CompareController.java` | 210-216（finally 内） | `new Thread(() -> { try { TimeUnit.MINUTES.sleep(5); PROGRESS_CACHE.remove(username); RESULT_CACHE.remove(username); } catch (InterruptedException ignored) {} }).start();` | `CLEANUP_SCHEDULER.schedule(() -> { PROGRESS_CACHE.remove(username); RESULT_CACHE.remove(username); }, 5, TimeUnit.MINUTES);` | 改为共享守护线程池调度，避免高并发线程堆积；无关闭钩子需求（守护线程随 JVM 退出） |

> 业务语义不变：仍是 5 分钟后清理当前用户的 PROGRESS_CACHE / RESULT_CACHE。
> 注：原 `new Thread(...)` 未捕获 `InterruptedException` 之外的异常、且非守护线程；新方案统一由共享池管理。

---

## 三、P4 — Excel 读取行数上限 + zip bomb 防护（OOM/DoS 防护）

| 文件 | 行 | 原片段 | 新片段 | 影响 |
|------|----|--------|--------|------|
| `controller/CompareController.java` | 14（import） | `import org.apache.poi.xssf.usermodel.XSSFWorkbook;` | 新增 `import org.apache.poi.util.ZipSecureFile;` | 支持 zip bomb 防护 |
| `controller/CompareController.java` | 318-319（新增常量） | 无 | `private static final int MAX_EXCEL_ROWS = 50_000;` | 行数硬上限（防 OOM） |
| `controller/CompareController.java` | 321-339（readFirstColumnNames） | `Workbook wb = WorkbookFactory.create(file.getInputStream());`<br>`try { Sheet sheet = wb.getSheetAt(0);`<br>`boolean skipHead = true;`<br>`for (int r = 0; r <= sheet.getLastRowNum(); r++) {` | 在 `create` 前加 `ZipSecureFile.setMinInflateRatio(0.005);`；<br>`create` 后 `if (sheet.getLastRowNum() > MAX_EXCEL_ROWS) throw new IllegalArgumentException("Excel 行数超过上限 " + MAX_EXCEL_ROWS);`；<br>循环内 `if (r > MAX_EXCEL_ROWS) throw new IllegalArgumentException(...);` | **zip bomb 防护 + 行数上限受控异常** |

> 异常由 `compareExcel` 的 `catch (Exception e)` 兜底返回 `AjaxResult.error("比对失败：" + e.getMessage())`，**未吞异常**。
> 仅读首列（单列），单元格数量风险低，未额外限制。
> `ZipSecureFile.setMinInflateRatio` 为静态全局设置，方法内每次调用设置无害。

---

## 四、编译验证

- 本机环境：**无 `mvn` / `mvnw` / JDK**（已探测 `command -v mvn`、`mvnw`、`java` 均不存在），无法本地编译。
- 代码已逐项人工复核：`import` 齐全、`()`/`{}` 括号匹配、方法签名与接口保持不变、`fastjson2` `JSON.parseObject(byte[], Class, Filter...)` 重载与 `JSONReader.AutoTypeFilter` 类型匹配。
- **编译结果：待 CI 编译验证**（非失败，属环境限制）。

---

## 五、结论

| 项 | 状态 |
|----|------|
| SEC5 去除 autoType（白名单：LoginUser/String/Integer） | 已落地 |
| P3 共享守护线程池替代每请求线程 | 已落地 |
| P4 Excel 行数上限 + zip bomb 防护 | 已落地 |
| 编译验证 | 待 CI（本环境无 Maven/JDK） |

**SEC5 枚举出的实际存储类型列表**：`com.prj.common.core.domain.model.LoginUser`、`java.lang.String`、`java.lang.Integer`（仅此 3 类，已全量纳入白名单）。

**IS_PASS: YES** — 三项 P1 加固均按最小变更原则落地，业务接口与语义保持不变，Redis 类型白名单覆盖全部实际存储类型，仅受环境限制需 CI 编译复核。
