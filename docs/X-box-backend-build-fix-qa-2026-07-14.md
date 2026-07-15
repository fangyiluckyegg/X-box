# X-box backend-c 编译修复 — QA 独立复核报告

- **复核人**：严过关（QA 工程师）
- **项目**：X-box / backend-c（`prj-backend-c`）
- **复核对象**：工程师寇豆码对 P1b 编译回归的修复
- **方式**：源码静态确权 + 全仓 Grep 残留扫描（本环境无 JDK/Maven，未实跑 `mvn compile`）
- **结论**：**IS_PASS: YES（代码级）**；智能路由 **NoOne**（6/6 项 PASS）

## 复核项与证据

### 1. RedisConfig import 与类型链 —— PASS
- 第 6 行 `import com.alibaba.fastjson2.reader.ObjectReader;` ✅ 包名 `com.alibaba.fastjson2.reader`（非 `com.alibaba.fastjson2`），拼写正确
- 第 79 行 `private static final ObjectReader AUTO_TYPE_WHITELIST = JSONReader.autoTypeFilter(...)` ✅ 不再用 `JSONReader.AutoTypeFilter`
- 第 113 行 `return JSON.parseObject(bytes, Object.class, AUTO_TYPE_WHITELIST);` ✅ 未改，与第 79 行类型闭合
- 类型链：`JSONReader.autoTypeFilter(String...)` 返回 `ObjectReader`；`JSON.parseObject(byte[], Class, ObjectReader)` 重载在 fastjson2 2.0.x 存在 → 编译应通过

### 2. CompareController 未被误改 —— PASS
- 第 14 行 `import org.apache.poi.util.ZipSecureFile;` 原样存在
- 第 325 行 `ZipSecureFile.setMinInflateRatio(0.005);` 原样存在
- `ZipSecureFile` 全仓仅 2 命中，均在 CompareController.java，无改动痕迹

### 3. pom POI 版本双保险 —— PASS
- dependencyManagement（56-66 行）：`poi` 5.2.3（60）、`poi-ooxml` 5.2.3（65）✅
- dependencies（217-227 行）：`poi` 5.2.3（221）、`poi-ooxml` 5.2.3（226）✅
- 两处均 5.2.3，无版本错位

### 4. fastjson2 版本一致性 —— PASS
- pom 第 43-47 行钉 `fastjson2` 2.0.53；dependencies 未写 version 继承 2.0.53 ✅
- 与 `ObjectReader` / `autoTypeFilter` API（2.0.x）匹配

### 5. 残留引用扫描 —— PASS
- Grep `AutoTypeFilter` 全 backend-c：**0 命中**（无 `JSONReader.AutoTypeFilter` 残留）
- Grep `ZipSecureFile` 全 backend-c：**仅 2 命中**（CompareController 14/325 行），符合预期

### 6. 已知限制（不阻塞）
- 本环境无 JDK/Maven，无法实跑 `mvn compile`；上述 PASS 基于静态代码级确权
- 需在 dev 容器执行 `mvn clean` → 清 `~/.m2/repository/org/apache/poi` → 重新 `build` 做最终兜底
- 原根因已闭环：POI 钉 5.2.3 后 `ZipSecureFile` 可解析；fastjson2 改用 `ObjectReader` 接收 `autoTypeFilter` 返回值后 `AutoTypeFilter` 符号缺失已消除

## 路由判定：NoOne
全部 6 项 PASS；唯一未覆盖项（无实编译）属已知限制、不判 FAIL。
建议：在 dev 容器按第 6 项步骤跑一次真实 build 作为发布前最终门禁；若仍编译失败/502，再路由工程师精准返工。
