# X-box P1 阶段二第一批 PHP 安全加固 — QA 独立复核报告

- **日期**：2026-07-14
- **复核人**：严过关（QA Engineer）
- **复核方式**：目视 `Read` 实际文件取证 + `grep -rn` 全盘复扫（本环境无 PHP 运行能力，语法正确性靠目视检查）
- **复核原则**：逐项"证伪/证实"，不橡皮图章

## 复核范围
- `Niu_Txl/902/message/Connections/conn.php`
- `Niu_Txl/902/work/Connections/conn.php`
- `Niu_Txl/902/message/index.php`
- `Niu_Txl/902/message/reply-msg.php`
- `Niu_Txl/902/message/add-msg.php`
- 全局 `grep -rn` 复扫 `Niu_Txl/902`（留言内容相关裸输出）

---

## F1 — L1 错误信息泄露（conn.php ×2）

**声明**：连接失败分支由 `die(mysqli_connect_error())` 改为 `error_log(...)` + `http_response_code(500)` + 泛化 `die(...)`，不再回显 DB 细节。

**取证**：
- `grep -rn "mysqli_connect_error" Niu_Txl/902` 全目录仅 **2 处**命中，均在 `error_log()` 内部：
  - `message/Connections/conn.php:18` → `error_log('DB connect failed: ' . mysqli_connect_error());`
  - `work/Connections/conn.php:18` → 同上
  - **无任何裸 `die(mysqli_connect_error())` 残留**。
- 连接失败分支（`message/conn.php:17-21`、`work/conn.php:17-21`）：
  ```php
  $conn = mysqli_connect($hostname_conn, $username_conn, $password_conn, $database_conn);
  if (!$conn) {
      error_log('DB connect failed: ' . mysqli_connect_error());
      http_response_code(500);
      die('服务暂时不可用,请稍后重试');
  }
  ```

**判定：PASS** ✅

---

## F2 — SEC12 空密码兜底（conn.php ×2）

**声明**：读取 `CLASS_DB_PWD` 后 fail-closed——缺失/空即 `error_log` + 500 拒绝连接；不再有 `?: ''` 空密码兜底。

**取证**：
- `message/conn.php:8`、`work/conn.php:8`：
  ```php
  $password_conn = getenv('CLASS_DB_PWD');   // 无 ?: '' 兜底
  ```
- 失败分支（`message/conn.php:9-13`、`work/conn.php:9-13`）：
  ```php
  if ($password_conn === false || $password_conn === '') {
      error_log('CLASS_DB_PWD is missing or empty; refusing to connect');
      http_response_code(500);
      die('服务配置缺失,无法连接数据库');
  }
  ```
- `grep -rn "CLASS_DB_PWD" Niu_Txl/902` 仅命中 line 8（读取）与 line 10（error_log），**确认无 `?: ''` 空密码兜底残留**。

**判定：PASS** ✅

---

## F3 — SEC11 存储型 XSS

**声明**：`index.php` 渲染端对 `P_Pic/P_Name/P_Mail/P_Content`（约 5 处）加 `htmlspecialchars($x, ENT_QUOTES, 'UTF-8')`；`reply-msg.php` 反射的 `$_GET['P_ID']` 也转义；`add-msg.php` 无原始用户输入裸回显，未改。

**取证（声明内三项）**：
- `index.php`：
  - L99 `echo htmlspecialchars($row_rsview['P_Pic'], ENT_QUOTES, 'UTF-8');` ✅
  - L100 `echo htmlspecialchars($row_rsview['P_Name'], ENT_QUOTES, 'UTF-8');` ✅
  - L101 `echo htmlspecialchars($row_rsview['P_Mail'], ENT_QUOTES, 'UTF-8');` ✅
  - L110 / L114 `echo htmlspecialchars($row_rsview['P_Content'], ENT_QUOTES, 'UTF-8');`（私密/公开两分支均转义）✅
- `reply-msg.php`：
  - L105 `<input ... value="<?php echo htmlspecialchars($_GET['P_ID'], ENT_QUOTES, 'UTF-8'); ?>">` ✅
- `add-msg.php`：
  - 仅 `echo $editFormAction`（其中 `$_SERVER['QUERY_STRING']` 已 `htmlentities`），无用户留言内容裸回显 ✅

**判定（声明内三项）：PASS** ✅

### F3 残留缺口（关键，路由依据）
`grep -rn` 复扫发现 **SEC11 目标未完全达成**：

- **`Niu_Txl/902/message/reply.php`（被 `index.php:117` `include("reply.php")` 引入留言板）L18：**
  ```php
  <div id="reply-text"><strong>管理员回复：</strong><?php echo $row_rsreply['R_Content']; ?></div>
  ```
  `R_Content` 为用户经 `reply-msg.php:54`（`$_POST['textarea']` → `GetSQLValueString(...,"text")`）提交的**回复内容**，此处**裸输出、未转义** → 存储型 XSS。SEC11 仅覆盖了主留言 `P_Content`，**漏掉了回复内容 `R_Content`**。
- `reply.php:19` `echo $row_rsreply['R_Date'];` 亦裸输出（日期，服务端 `date()` 生成，风险低但风格不一致）。
- 备注：`index.php:104` `echo $row_rsview['P_Date'];` 裸输出，但 `P_Date` 由服务端 `date("Y:m:d H:i:s")` 生成、非用户输入，风险极低。

### 其他遗留（不在声明范围，列入 backlog）
- `index copy.php`、`reply-msg copy.php`：web 根目录下的备份副本，含原始漏洞代码（`P_Pic/P_Name/P_Mail/P_Content` 及 `$_GET[P_ID]` 裸输出），建议删除。
- `work/admin/fupload.php` L72、L92-95：多处 `echo $_GET[...]`（`ImgS`/`ImgW`/`ImgH`/`useForm`/`upUrl`/`prevImg`/`reItem`）裸输出，反射型 XSS，位于 `work/admin` 后台，**超出本批留言板范围**，建议后续批次处理。

---

## 版本控制检查
- `git status --porcelain` 输出含 `?? Niu_Txl/`，**确认未执行 `git add`**，`Niu_Txl` 仍整体显示为未跟踪。**符合要求** ✅

---

## 整体路由判定
> **Engineer**

**理由**：F1 / F2 / F3 声明本身取证全部正确（PASS）；但 SEC11 目标未完全达成——`reply.php` 仍存在用户内容 `R_Content` 裸输出（存储型 XSS），属"用户内容未转义"，触发路由规则"任一项仍 FAIL → Engineer"。需工程师补齐一处 `htmlspecialchars($row_rsreply['R_Content'], ENT_QUOTES, 'UTF-8')`（同 TinyMCE 取舍：纯文本转义，富文本待 HTMLPurifier）。

## 一句话结论
三项声明均属实，但回复内容 `R_Content` 漏网未转义，存在可被利用的存储型 XSS，须回工程师补齐后复测；TinyMCE 富文本按字面显示属已确认的安全优先取舍，待后续 HTMLPurifier 白名单解决。

## TinyMCE 取舍说明
留言板采用 TinyMCE，对 `P_Content` / `R_Content` 做 `htmlspecialchars` 纯文本转义会使富文本标签按字面显示（不再渲染）。此为安全优先的合理取舍，工程师已备注后续引入 HTMLPurifier 白名单。判定为**已知限制，标"待后续"而非 FAIL**。

---

## 复测记录（Round 2，2026-07-14）

工程师修复后回归复核（严过关）：

1. **reply.php 存储型 XSS（原 F3 残留缺口）— 已闭环 ✅**
   - `reply.php:18` `echo htmlspecialchars($row_rsreply['R_Content'], ENT_QUOTES, 'UTF-8');`
   - `reply.php:19` `echo htmlspecialchars($row_rsreply['R_Date'], ENT_QUOTES, 'UTF-8');`
   - 留言板主留言 `P_Content` 与回复 `R_Content` 均已覆盖，SEC11 目标达成。

2. **死副本清理 — 已确认 ✅**
   - `grep -rn "copy\.php" Niu_Txl/902/message` → **No matches**，确认 `index copy.php`、`reply-msg copy.php`、`add-msg copy.php`、`del-msg copy.php`、`login copy.php`、`reply copy.php` 共 6 个含原始漏洞代码的副本已删除，活跃逻辑不受影响。

3. **回归复扫（留言板范围，仅剩非 XSS 项）**
   - `index.php:103` / `:118` `echo $row_rsview['P_ID']`：P_ID 为自增整型（非用户可控 HTML），置于 href 查询参数中，非 XSS 注入点。
   - `index.php:104` `echo $row_rsview['P_Date']`：日期由服务端 `date()` 生成、非用户输入，风险极低。
   - 其余用户可控内容（P_Pic/P_Name/P_Mail/P_Content、R_Content/R_Date、反射 `$_GET['P_ID']`）全部经 `htmlspecialchars(ENT_QUOTES,'UTF-8')` 转义。

4. **遗留（超出本批范围，已记入 backlog，非 FAIL）**
   - `work/admin/fupload.php`（L72、L92-95）多处裸 `echo $_GET[...]` 属后台反射 XSS，工程师按约定**未在本批处理**，已记入变更清单"后续批次"。同目录可能还有其他 `* copy.php`/`* 副本.php`，建议另立清理批次。

### Round 2 路由判定
> **NoOne（全部通过）**

三项声明（F1/F2/F3）取证正确，原 SEC11 残留缺口（reply.php R_Content）已修复并复测通过，死副本已清理。本批 P1 PHP 安全加固（留言板范围）**通过 QA 复核**。TinyMCE 富文本按字面显示为已确认安全取舍，待 HTMLPurifier 解决。
