# X-box P1 第三批安全收尾实施记录（2026-07-14）

- **团队**：`software-xbox-p1c`（主理人齐活林 + 工程师寇豆码 + QA 严过关）
- **范围**：纯 PHP，仅 `Niu_Txl/` 模块；无编译、无 Docker 实栈、未触碰 Java/前端/compose/nginx
- **输入**：延续 P1 安全主线（第一批 PHP、第二批 Java 已交付），收尾报告 backlog 中剩余纯 PHP 项
- **IS_PASS**：工程师 `YES` → QA 独立复核 7 项全 `PASS`，路由 `NoOne`

---

## 任务 A：修复 `fupload.php` 反射型 XSS

**文件**：`Niu_Txl/902/work/admin/fupload.php`（唯一被修改的 PHP）

### 用户可控输出点（共 7 处，全部闭环）
| # | 变量 | 上下文 | 转义函数 |
|---|------|--------|----------|
| 1 | `ImgS` | `onSubmit="...'VALUE'..."`（JS 单引号串内嵌 HTML 属性） | `esc_js_str()` |
| 2 | `ImgW` | 同上 | `esc_js_str()` |
| 3 | `ImgH` | 同上 | `esc_js_str()` |
| 4 | `useForm` | 隐藏域 `value="..."`（HTML 属性） | `esc_attr()` |
| 5 | `upUrl` | 隐藏域 `value="..."` | `esc_attr()` |
| 6 | `prevImg` | 隐藏域 `value="..."` | `esc_attr()` |
| 7 | `reItem` | 隐藏域 `value="..."` | `esc_attr()` |

### 新增辅助函数（仅安全闭环，无新功能）
- `esc_attr($v)` = `htmlspecialchars($v, ENT_QUOTES, 'UTF-8')`，用于 HTML 属性上下文（4 个隐藏域）。
- `esc_js_str($v)` = 先转义 JS 单引号串特殊字符（`\ ' \r \n \t`），再 `htmlspecialchars($v, ENT_COMPAT, 'UTF-8')`，用于「JS 串内嵌 HTML 属性」双层上下文（ImgS/ImgW/ImgH）。
- 顺序正确性：JS 转义在前、HTML 转义在后。若反序（`'` 经 `ENT_QUOTES` 变 `&#39;`，HTML 解析阶段还原为 `'` 击穿 JS 单引号串），XSS 仍失效。此分层为该嵌套上下文的正确闭环。
- 全部 7 处读取均加 `isset()` 守卫（无 Undefined index Notice）。

### 依赖副作用（已知、P0 有意决策）
- `fupload.php` 表单 `ACTION="fupaction.php"`，而 `fupaction.php` 在 P0 已隔离为 `Niu_Txl/_quarantine/902_fupaction.php.disabled`。
- 全仓唯一代码引用即此行 → 上传链路当前失效。本次未改动 `ACTION`（超出范围，且不应擅自恢复安全隔离对象）。
- **待决策**：若后续需恢复上传能力，须先决定 `fupaction.php` 去留（建议按 P0 口径永久废弃或重写）。

---

## 任务 B：隔离 `902/work/` 与 `607/` 下 copy 死副本

**铁律落实**：全部「移动隔离」而非硬删（复制到 `Niu_Txl/_quarantine/` 对应子目录 + README，可还原，全程无 `rm`）。

### 隔离清单（原路径 → 目标路径）
**`902/work/`（→ `_quarantine/902_work_copies/`，5 个）**
| 原路径 | 目标路径 |
|--------|----------|
| `902/work/index copy.php` | `902_work_copies/index copy.php.disabled` |
| `902/work/list copy.php` | `902_work_copies/list copy.php.disabled` |
| `902/work/work-all copy.php` | `902_work_copies/work-all copy.php.disabled` |
| `902/work/work-show copy.php` | `902_work_copies/work-show copy.php.disabled` |
| `902/work/work-type copy.php` | `902_work_copies/work-type copy.php.disabled` |

**`607/Dad's/`（→ `_quarantine/607_copies/`，1 个 PHP + 2 个 HTML）**
| 原路径 | 目标路径 |
|--------|----------|
| `607/Dad's/index - 副本.php` | `607_copies/index - 副本.php.disabled` |
| `607/Dad's/index - 副本.html` | `607_copies/index - 副本.html` |
| `607/Dad's/index - 副本 (2).html` | `607_copies/index - 副本 (2).html` |

### 死副本判定依据
- **902/work 5 副本**：与同名权威源比对，差异全部为副本使用 PHP 7.0+ 已移除的 `mysql_*` 废弃函数（运行即 fatal error），权威源已升 `mysqli_*`；全仓零引用，无独立业务逻辑。
- **607/Dad's 3 副本**：md5 与权威源字节完全一致（`index - 副本.php`==`index.php`=`bfa11fbc573946beecc99fa2b0f62285`；两个 html 副本均 ==`index.html`=`c4fe7d2323cc3c36df49433e5df1f15c`）；零引用。

### 排除未动的疑似副本（非死副本）
- `607/永远的607/oldphoto/`：32 个真实年级照片资产，内容目录非单文件副本。
- 顺序编号图片（`p1(M).JPG` 等）：独立上传物，无同名基准被重复。
- `607` 顶层及子目录的独立 `.html`（photo.html、实验.html 等）：不匹配副本命名模式或无可还原基准。

---

## ADVISORY 修正（对齐 P0 F1 铁律）
- QA 指出 6 个隔离副本仍带 `.php` 扩展名，打破 P0「隔离区 0 个 `.php`」不变量（虽 `.htaccess` 已递归禁执行、访问 403，无实际风险）。
- 已续派工程师将 6 个 `.php` 副本改名 `.php.disabled`（仅扩展名，内容不变），更新两个 README 映射表。
- 主理人独立验证：`find _quarantine -name '*.php'` = 0（与 P0 F1 不变量一致），6 个 `.php.disabled` 文件在位，`.disabled` 非可执行扩展名叠加 `.htaccess` 递归保护，零风险。

---

## 交付状态
- 修改文件：`Niu_Txl/902/work/admin/fupload.php`（1 个）
- 隔离文件：`Niu_Txl/_quarantine/` 下 8 个死副本 + 2 份 README
- 跨文件一致性：副本隔离未破坏任何引用（全量 grep 零引用）；权威源文件完好
- 可逆性：全部移动 + README，无 `rm`，还原 = 移回原目录
- 未 git 提交（Niu_Txl 未跟踪，按铁律）

> 注：隔离 README 内年份误写为 `2025-07-14`，实际为 `2026-07-14`，仅为文档笔误，不影响安全，可后续修正。
