# X-box P1 第三批 QA 独立复核报告（2026-07-14）

- **QA**：严过关　**复核对象**：工程师寇豆码 P1 收尾第三批产出
- **项目路径**：`D:\crh123dexiaohao\X-box`（`Niu_Txl/` 为 PHP 7.4 遗留模块，未 git 跟踪）
- **复核方式**：逐项 Read 源码 + 全仓 Grep + md5 独立复算 + Bash/Glob 取证。未采信工程师自述。
- **环境限制**：本环境无 PHP 运行时，XSS 正确性最终依据为代码级逐字符推演 + HTML/JS 双阶段解析语义推导。

---

## 总判定：IS_PASS = YES（7 项全 PASS；1 项 ADVISORY 不阻塞）
**智能路由：NoOne**（源码无功能性缺陷需返工；唯一副作用为 P0 有意决策；另附 1 条可选一致性建议）

---

### 1. XSS 闭环正确性 —— PASS
- 7 处用户可控输出点全部经转义，无残留裸 `echo $_GET/$_POST/$_REQUEST`。
  - `ImgS/ImgW/ImgH` → `fupload.php:91`，走 `esc_js_str()`（双层上下文）
  - `useForm/upUrl/prevImg/reItem` → `fupload.php:111-114`，走 `esc_attr()`
- **esc_js_str 防 XSS 推演**（输入 `'";<script>alert(1)</script>`）：
  - 先 JS 转义：`'`→`\'` → `\'";<script>alert(1)</script>`
  - 再 HTML 转义(ENT_COMPAT)：`"`→`&quot;`、`<`→`&lt;`、`>`→`&gt;` → `\'&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;`
  - 浏览器先解析 HTML 属性（`&quot;` 解码为属性值内容不破界），再交 JS：`\'` 为被转义单引号，串内文本不执行 → **不可触发 XSS**。
  - 输入 `';alert(1);'` → `\'alert(1);\'`：被转义引号包裹的纯文本 → **不可触发**。
  - 输入 `\` → `\\`：攻击者无法用单 `\` 中和后续 `\'` → 串边界稳固。

### 2. esc_js_str 实现审查 —— PASS
- `esc_attr`：`htmlspecialchars((string)$v, ENT_QUOTES, 'UTF-8')` ✅
- `esc_js_str`：先 `str_replace` 转义 `\ ' \r \n \t`，再 `htmlspecialchars($v, ENT_COMPAT, 'UTF-8')` ✅
- **顺序正确性**：JS 在前、HTML 在后，为此嵌套上下文正确顺序（反序则 `'`→`&#39;` 还原击穿 JS 串）。工程师「ENT_QUOTES 单独不足、需分层」判断成立 ✅
- 函数全仓仅定义于 `fupload.php`，该页无 include/require → 无重定义致命风险 ✅

### 3. isset 守卫 —— PASS
- 全部 7 处读取均为 `isset($_GET['X']) ? $_GET['X'] : ''` 形式 → 无 Undefined index Notice ✅

### 4. 副本隔离真实性 —— PASS（附 ADVISORY）
- **零引用**（全仓含 .html 等所有文本类型）：对 8 个副本的 grep 命中仅存在于 `docs/`（文档，非功能引用）与 README 自身 → 隔离不破坏任何链路 ✅
- **死副本判定抽样核实**：
  - 902/work 5 副本：grep `mysql_*` 全部命中（`mysql_select_db/query/fetch_assoc/num_rows/free_result/error`）→ PHP 7.0+ 已移除，运行 fatal error ✅
  - 607/Dad's 3 副本：md5 独立复算完全吻合 README 声明 ✅
- **README 存在且清晰**：含来源、映射、原因、md5、还原方式 ✅
- **ADVISORY（不阻塞）**：隔离后 `find _quarantine -name '*.php'` 原 = **6**，打破 P0「隔离区 0 个 .php」不变量。但 `_quarantine/.htaccess`（`php_flag engine off` + `Require all denied`）递归生效 → 6 个 .php 不可执行、访问 403，实际风险被中和。建议改名 `.php.disabled` 与 P0 F1 完全一致（已采纳并修正）。

### 5. 排除项合理性 —— PASS
- `607/永远的607/oldphoto/`：32 个真实照片资源，内容目录非死副本 ✅
- 顺序编号图片（`p1(M).JPG` 等）：真实资产非重复 ✅

### 6. 依赖副作用确认 —— 已知安全副作用（不要求返工）
- `fupload.php:91` 仍 `ACTION="fupaction.php"`；全仓唯一代码引用即此行。
- `Niu_Txl/_quarantine/902_fupaction.php.disabled` 确实存在 → 上传链路当前失效，系 P0 有意隔离决策。
- 标注供主理人决策 fupaction.php 去留，不要求工程师返工 ✅

### 7. 可逆性 —— PASS
- 全程无 `rm`：源目录仅剩权威源；文件已移动至 `_quarantine` 子目录并附 README。还原 = 移回原目录。
- `.htaccess` 递归保护，误访问亦安全 ✅

---

## 路由结论
- **NoOne**：源码（A 的 XSS 闭环、B 的隔离）经独立取证全部正确，无需要工程师返工的功能性缺陷。
- 供主理人裁断的两点：
  1. 已知安全副作用：fupload.php → fupaction.php 链路失效（P0 有意，记录备查）。
  2. 可选一致性建议（已采纳）：6 个隔离副本改名 `.php.disabled` 对齐 P0 F1。

> 说明：本环境未安装 PHP，XSS 防穿透结论基于源码逐字符推演 + HTML/JS 双阶段解析语义，未做运行时动态验证；如需 100% 运行时证据，可在 PHP 7.4 环境用 `esc_js_str("';alert(1);'")` 打印复核（预期 `\'alert(1);\'`）。
