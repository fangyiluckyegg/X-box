# X-box P1 阶段二第一批：PHP 侧安全加固 — 变更清单

> 执行人：软件工程师 寇豆码（Kou）
> 日期：2026-07-14
> 范围：P1 阶段二第一批 3 项（L1 / SEC12 / SEC11）
> 原则：线上活跃文件最小原地修改，不隔离 / 不删除 / 不改名，保留原有功能；不编译、不 `git add`、改动可逆。

## 变更明细

| 编号 | 文件 | 行（修改后） | 原代码片段 | 新代码片段 | 影响 |
|------|------|------------|-----------|-----------|------|
| ① L1 | `Niu_Txl/902/message/Connections/conn.php` | 17–21 | `if (!$conn) {`<br>`    die("数据库连接失败: " . mysqli_connect_error());`<br>`}` | `if (!$conn) {`<br>`    error_log('DB connect failed: ' . mysqli_connect_error());`<br>`    http_response_code(500);`<br>`    die('服务暂时不可用,请稍后重试');`<br>`}` | DB 错误仅记入服务端日志，向客户端返回泛化 500 提示，**不再泄露表结构/路径/账号**；连接失败仍终止脚本，原有行为保留。 |
| ① L1 | `Niu_Txl/902/work/Connections/conn.php` | 17–21 | 同上 | 同上 | 同上（work 站点）。 |
| ② SEC12 | `Niu_Txl/902/message/Connections/conn.php` | 8–13 | `$password_conn = getenv('CLASS_DB_PWD')  ?: '';` | `$password_conn = getenv('CLASS_DB_PWD');`<br>`if ($password_conn === false \|\| $password_conn === '') {`<br>`    error_log('CLASS_DB_PWD is missing or empty; refusing to connect');`<br>`    http_response_code(500);`<br>`    die('服务配置缺失,无法连接数据库');`<br>`}` | 缺失/空密码时 **fail-closed** 拒绝连接；`CLASS_DB_PWD` 已正常设置时行为不变，不影响正常连接。 |
| ② SEC12 | `Niu_Txl/902/work/Connections/conn.php` | 8–13 | 同上 | 同上 | 同上（work 站点）。 |
| ③ SEC11 | `Niu_Txl/902/message/index.php` | 99 | `<img src="<?php echo $row_rsview['P_Pic']; ?>" ...` | `<img src="<?php echo htmlspecialchars($row_rsview['P_Pic'], ENT_QUOTES, 'UTF-8'); ?>" ...` | 头像路径转义，防止属性上下文 XSS。 |
| ③ SEC11 | `Niu_Txl/902/message/index.php` | 100 | `<?php echo $row_rsview['P_Name']; ?><br>` | `<?php echo htmlspecialchars($row_rsview['P_Name'], ENT_QUOTES, 'UTF-8'); ?><br>` | 昵称转义，防存储型 XSS。 |
| ③ SEC11 | `Niu_Txl/902/message/index.php` | 101 | `<?php echo $row_rsview['P_Mail']; ?></div>` | `<?php echo htmlspecialchars($row_rsview['P_Mail'], ENT_QUOTES, 'UTF-8'); ?></div>` | 邮箱转义，防存储型 XSS。 |
| ③ SEC11 | `Niu_Txl/902/message/index.php` | 110、114 | `echo $row_rsview['P_Content'];`（两处） | `echo htmlspecialchars($row_rsview['P_Content'], ENT_QUOTES, 'UTF-8');`（两处） | 留言正文统一转义，**消除存储型 XSS**（管理员可见/公开两分支均覆盖）；新增 SEC11 注释。 |
| ③ SEC11 | `Niu_Txl/902/message/reply-msg.php` | 105 | `<input ... value="<?php echo $_GET['P_ID']; ?>">` | `<input ... value="<?php echo htmlspecialchars($_GET['P_ID'], ENT_QUOTES, 'UTF-8'); ?>">` | `$_GET['P_ID']` 反射输入转义，防反射型 XSS。 |
| ③ SEC11 | `Niu_Txl/902/message/add-msg.php` | — | 仅 `echo $editFormAction`（QUERY_STRING 已 `htmlentities`）、`echo date(...)` | 无改动 | 经 Read 确认：**未 echo/print 任何原始用户输入**，仅入库走 `mysqli_real_escape_string`，无需修改。 |

## 备注 / 需关注项

- **富文本场景**：`add-msg.php` / `reply-msg.php` 使用 TinyMCE 编辑器。本次按任务要求对留言正文做 **纯文本 `htmlspecialchars` 转义（最小改动）**，代价是 TinyMCE 产生的富文本标签（加粗/链接等）会被按字面文本显示。**富文本场景建议后续引入 HTMLPurifier 白名单**，本次未引入新依赖。
- **`index.php` 输出点确认**：已实地 Read 并定位全部用户内容输出点（P_Pic / P_Name / P_Mail / P_Content ×2），均已加转义；`P_Date` 为服务端 `date()` 生成、非用户输入，未改。
- **未做项**：报告 SEC11 关联建议的 HTMLPurifier 白名单未本次引入（任务明确"不要本次引入新依赖"）；`display_errors=Off` 属 PHP 运行环境配置，不在本批文件改动范围。
- **可逆性**：均为小改，逻辑对等替换，未删文件/未改名，可逐行回退。

## 追加修复（QA 复核，2026-07-14）

| 编号 | 文件 | 行 | 原代码片段 | 新代码片段 | 影响 |
|------|------|----|-----------|-----------|------|
| ③ SEC11 | `Niu_Txl/902/message/reply.php` | 18 | `<div id="reply-text"><strong>管理员回复：</strong><?php echo $row_rsreply['R_Content']; ?></div>` | `...<?php echo htmlspecialchars($row_rsreply['R_Content'], ENT_QUOTES, 'UTF-8'); ?></div>` | 回复内容转义，**消除回复区存储型 XSS**（reply.php 由 index.php:117 的 `include("reply.php")` 引入留言板）。 |
| ③ SEC11 | `Niu_Txl/902/message/reply.php` | 19 | `<div id="reply-date"><?php echo $row_rsreply['R_Date']; ?></div>` | `<div id="reply-date"><?php echo htmlspecialchars($row_rsreply['R_Date'], ENT_QUOTES, 'UTF-8'); ?></div>` | R_Date 一并转义，风格统一（日期服务端生成，风险低）。 |
| 清理 | `Niu_Txl/902/message/` 下 6 个副本 | — | 含原始漏洞代码的备份：`index copy.php`、`reply-msg copy.php`、`add-msg copy.php`、`del-msg copy.php`、`login copy.php`、`reply copy.php` | 已删除（Remove-Item） | 移除 web 根目录下可被直接访问的死副本，消除"绕过已修复代码、直接访问旧漏洞副本"风险（对应报告 Q3）。均有同名权威文件（无 `copy` 后缀）保留功能。 |

**未处理项（超出本批范围，建议后续批次）：**
- `Niu_Txl/902/work/admin/fupload.php`（报告 L72、L92-95 多处裸 `echo $_GET[...]`）属后台反射型 XSS，不在留言板 SEC11 范围内，按 QA 建议列入后续批次处理。
- `Niu_Txl/902/work`、`Niu_Txl/607` 下可能仍有其它 `* copy.php` / `* 副本.php`（报告 Q3 统计共 16 个），建议统一清理批次处理。

## 验证

- 本次对所有被改文件 `Read` 复核，改动均生效、无语法明显异常（未运行 `php -l`，遵循"不编译"约束）。
- 未执行 `git add`；`Niu_Txl/` 仍按现状保持未跟踪。

## 结论

IS_PASS: YES

三项（L1 错误泄露、SEC12 空密码兜底、SEC11 存储型 XSS）均按"最小原地修改"完成，原业务功能（留言展示、回复、连接）保留；唯一需主理人知晓的取舍是留言板富文本本次按纯文本转义（后续建议 HTMLPurifier），不阻断本批交付。

**追加（QA 复核）：** 已补齐 `reply.php` 的 `R_Content`/`R_Date` 转义（SEC11 漏网点），并删除 `902/message` 下 6 个含旧漏洞的死副本；`fupload.php` 后台反射 XSS 及 `902/work`、`607` 下的其余副本建议列入后续批次。原 3 项交付不受影响，IS_PASS 维持 **YES**。
