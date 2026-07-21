# X-box backend-c 编译失败修复记录（P1b 回归）

- **日期**：2026-07-14（北京时间 7-15 凌晨收尾）
- **类型**：BugFix（P1b Java 安全加固遗留的编译回归）
- **触发**：dev 环境 `docker compose ... up -d --build` 构建 `prj-backend-c` 时 Maven 编译失败，镜像未构建 → nginx 反代 `/captchaImage` 到 `prj-backend-c:8080` 报 **502 Bad Gateway**
- **团队**：`software-bugfix-backend-build`（主理人齐活林 / 工程师寇豆码 / QA 严过关）
- **结论**：工程师 IS_PASS: YES → QA 路由 NoOne（6/6 项 PASS，代码级）

## 一、原始编译错误（用户 dev 容器 `mvn` 输出）

```
[ERROR] CompareController.java:[14,27] cannot find symbol  symbol: class ZipSecureFile  location: package org.apache.poi.util
[ERROR] RedisConfig.java:[78,40] cannot find symbol  symbol: class AutoTypeFilter  location: class com.alibaba.fastjson2.JSONReader
[ERROR] CompareController.java:[325,9] cannot find symbol  symbol: variable ZipSecureFile  location: class com.prj.controller.CompareController
[ERROR] BUILD FAILURE
```

## 二、根因分析

| 错误 | 根因 | 性质 |
|------|------|------|
| `RedisConfig.java:78 AutoTypeFilter` | P1b SEC5 改 fastjson2 时把 `autoTypeFilter(...)` 的返回值赋给了一个不存在的 `JSONReader.AutoTypeFilter` 类型。fastjson2 2.0.x 中 `JSONReader` 无此嵌套类；`autoTypeFilter(String...)` 返回 `com.alibaba.fastjson2.reader.ObjectReader` | **确定性代码 bug** |
| `CompareController.java:14/325 ZipSecureFile` | import 与 `ZipSecureFile.setMinInflateRatio(0.005)` 用法均正确，POI 钉版 5.2.3（有该类）。编译报"找不到类"说明用户 Maven 本地仓库实际解析到的 poi 核心包版本过低/ jar 不完整（poi-ooxml 能编译，仅 poi 核心包缺该类） | **依赖版本/本地仓库** |

> 注：P1b 阶段本环境无 JDK/Maven，仅做代码级审查未真编译，故当时未发现这两处编译失败；今日 dev 真实构建暴露。

## 三、修复内容

### 文件 1：`backend/prj-backend-c/src/main/java/com/prj/framework/config/RedisConfig.java`（确定性编译修复）

新增 import（第 4 行后）：
```java
import com.alibaba.fastjson2.reader.ObjectReader;
```

第 78→79 行类型声明修正：
```java
// 改前（编译失败）
private static final JSONReader.AutoTypeFilter AUTO_TYPE_WHITELIST = JSONReader.autoTypeFilter(
        "com.prj.common.core.domain.model.LoginUser",
        "java.lang.String",
        "java.lang.Integer"
);
// 改后（正确）
private static final ObjectReader AUTO_TYPE_WHITELIST = JSONReader.autoTypeFilter(
        "com.prj.common.core.domain.model.LoginUser",
        "java.lang.String",
        "java.lang.Integer"
);
```
第 113 行 `JSON.parseObject(bytes, Object.class, AUTO_TYPE_WHITELIST);` 未改 —— `AUTO_TYPE_WHITELIST` 现为 `ObjectReader` 类型，与 `JSON.parseObject(byte[], Class, ObjectReader)` 重载匹配。白名单语义（仅 LoginUser/String/Integer）完全保留。

### 文件 2：`backend/prj-backend-c/pom.xml`（依赖版本双保险）

dependencies 块（约第 217-227 行）的 `poi` / `poi-ooxml` 显式加 `<version>5.2.3</version>`（dependencyManagement 原钉版 5.2.3 保留不变，二者一致）：
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi</artifactId>
    <version>5.2.3</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.3</version>
</dependency>
```

### 未改动
- `CompareController.java` 第 14 行 `import org.apache.poi.util.ZipSecureFile;` 与第 325 行 `ZipSecureFile.setMinInflateRatio(0.005);` 原样未动（Java 层面该 bug 无需修改，仅由 pom 版本双保险 + 清本地仓库解决）。

## 四、验证限制（重要）

本环境无 JDK/Maven，未实跑 `mvn compile`，仅做静态代码级正确性保证。需在 **dev 容器**执行最终兜底验证：

```bash
# 1. 清后端构建缓存
mvn clean
# 2. 清 poi 本地仓库缓存（确保 5.2.3 core jar 重新拉取，非旧版/残缺 jar）
rm -rf ~/.m2/repository/org/apache/poi
# 3. 重新构建 backend-c
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d --build prj-backend-c
```

预期：RedisConfig 编译错误（AutoTypeFilter）直接消除（确定性修复）；ZipSecureFile 错误在 poi 5.2.3 core jar 就绪后消除。backend-c 镜像构建成功 → 刷新 X-box 工具箱登录页 `/captchaImage` 不再 502。

## 五、文件清单

| 操作 | 文件 |
|------|------|
| 修改 | `D:\crh123dexiaohao\X-box\backend\prj-backend-c\src\main\java\com\prj\framework\config\RedisConfig.java` |
| 修改 | `D:\crh123dexiaohao\X-box\backend\prj-backend-c\pom.xml` |
| 未改 | `D:\crh123dexiaohao\X-box\backend\prj-backend-c\src\main\java\com\prj\controller\CompareController.java` |

未做 git add/commit（按用户习惯后续统一提交）。
