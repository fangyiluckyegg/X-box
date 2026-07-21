# X-box 后端 Java 加固 独立复核报告（QA：严过关）

- **日期**：2026-07-14
- **复核对象**：工程师寇豆码声明的三处加固（SEC5 / P3 / P4）
- **复核方式**：Read 实际源文件 + grep 取证，逐项 PASS/FAIL
- **环境限制**：本环境无 JDK / Maven，**编译与运行时验证待 CI**，不计入 FAIL

---

## SEC5 — RedisConfig 反序列化类型白名单

**声明**：移除 `JSONReader.Feature.SupportAutoType`，改用 `JSONReader.autoTypeFilter(...)` 白名单（LoginUser / String / Integer）；`serialize` 保留 `WriteClassName`。

**实际取证**：
- `RedisConfig.java:112` `deserialize`：`return JSON.parseObject(bytes, Object.class, AUTO_TYPE_WHITELIST);` —— **无** `SupportAutoType` 调用。
- `RedisConfig.java:78-82` 白名单：`"com.prj.common.core.domain.model.LoginUser"` / `"java.lang.String"` / `"java.lang.Integer"`。
- 白名单 FQN 与真实包一致：`grep -rn "class LoginUser"` → `com/prj/common/core/domain/model/LoginUser.java:26`，拼写与路径**完全一致**。
- `grep -rn "SupportAutoType" src/main/java`：仅 3 处**注释**（RedisConfig.java:29/63/110），**无任何实际开启点**。
- `RedisConfig.java:93` `serialize`：`JSON.toJSONBytes(object, JSONWriter.Feature.WriteClassName)` 保留。

**判定：PASS** ✅

---

## P3 — CompareController 每请求线程移除

**声明**：新增共享守护线程 `ScheduledExecutorService` 静态单例；`finally` 内 `new Thread(...).start()` 改为 `CLEANUP_SCHEDULER.schedule(...)`；无残留 `new Thread(`。

**实际取证**：
- `CompareController.java:32-33` import：`java.util.concurrent.Executors`、`ScheduledExecutorService`、`TimeUnit` 齐备。
- `CompareController.java:82-87` 静态 `CLEANUP_SCHEDULER`：`Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "compare-cache-cleaner"); t.setDaemon(true); return t; })` —— 单例 + 守护线程 ✅。
- `CompareController.java:226` `finally` 内：`CLEANUP_SCHEDULER.schedule(() -> { PROGRESS_CACHE.remove(username); RESULT_CACHE.remove(username); }, 5, TimeUnit.MINUTES);` —— 已替换，无每请求 `new Thread().start()` ✅。
- ⚠️ **措辞偏差（非 FAIL）**：`grep -n "new Thread("` **非空** —— 命中 `CompareController.java:84`（位于上述 `ThreadFactory` 内，是构建守护线程池的固有写法，**非每请求线程**）。原 per-request 派生线程已彻底移除，第 84 行属良性，不构成资源泄漏。

**判定：PASS**（附披露：grep 字面非空，但仅余调度器工厂内的守护线程，安全目标达成）

---

## P4 — CompareController Excel 读取防护

**声明**：`readFirstColumnNames` 新增 `MAX_EXCEL_ROWS`(50000)；`WorkbookFactory.create` 前 `ZipSecureFile.setMinInflateRatio(0.005)`；超限抛 `IllegalArgumentException`（上层 catch 兜底，未吞异常）。

**实际取证**：
- `CompareController.java:319`：`private static final int MAX_EXCEL_ROWS = 50_000;` ✅
- `CompareController.java:325`：`ZipSecureFile.setMinInflateRatio(0.005);` 位于 `WorkbookFactory.create(...)`（:326）**之前** ✅
- `CompareController.java:330-331`：`if (sheet.getLastRowNum() > MAX_EXCEL_ROWS) throw new IllegalArgumentException(...)`；循环内 :336-337 二次校验 `r > MAX_EXCEL_ROWS` 再次抛异常（双重防护）✅
- 异常未被吞：`readFirstColumnNames` 签名 `throws IOException`（:321），方法内仅 `finally { wb.close(); }`（:352-354）**不 catch**，异常向上抛；调用方 `compareExcel` 的 `catch (Exception e)`（:217）以 `AjaxResult.error` 兜底返回，未静默吞掉 ✅

**判定：PASS** ✅

---

## 路由判定

**NoOne（报告成功）** —— 三项安全目标均经源码与 grep 取证证实达成：SEC5 白名单正确且全局无 `SupportAutoType` 开启点、白名单类名与真实 `LoginUser` 包一致；P3 每请求线程已移除并改用共享守护调度器；P4 行数上限 + zip bomb 防护完好且异常不吞。

> 唯一需主理人知悉的偏差：P3 工程师“无残留 `new Thread(`”措辞不严谨 —— grep 字面仍有 1 处命中（:84），但位于 `CLEANUP_SCHEDULER` 的 `ThreadFactory` 内，是构造守护线程池的固有写法，属良性，不影响加固结论。

---

## 结论与待办

**结论**：IS_PASS 经验证为真，加固有效，路由 NoOne，可放行。

**待 CI 提醒**：本环境无 JDK/Maven，**编译验证待 CI**。请合并后在 CI 或本地执行：

```
mvn -pl prj-backend-c compile
# 启动 Spring Boot 验证运行时：确认登录态读写（LoginUser 白名单往返）、
# 比对接口缓存清理、超大/恶意 Excel 被拒绝。
```

- git 状态：` M`（已修改、**未 staged/未 commit**），符合“本环境未提交”说明，属正常。
