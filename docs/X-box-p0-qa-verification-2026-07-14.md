# X-box P0 安全止血修复 — QA 独立复核报告

- 复核日期：2026-07-14
- 复核人：严过关（software-qa-engineer）
- 复核原则：**用实际命令取证，证明而非确认；不橡皮图章**
- 仓库根：`D:/crh123dexiaohao/X-box`

## 取证命令清单（逐项可复现）

```bash
# 原路径危险文件是否已不存在
test -f Niu_Txl/info.php && echo STILL_THERE || echo GONE
test -f Niu_Txl/607/uploadHandler.php && echo STILL_THERE || echo GONE
test -f Niu_Txl/902/work/admin/fupaction.php && echo STILL_THERE || echo GONE
find Niu_Txl -type d -name _mmServerScripts            # 应为空
find Niu_Txl -name 'uploadHandler.php'                 # 应为空
find Niu_Txl -name 'fupaction.php'                     # 应为空

# 隔离区内容（扩展名须为非执行 .disabled/.txt）
ls -la Niu_Txl/_quarantine/

# .htaccess 内容核对
find Niu_Txl -name '.htaccess'
Read Niu_Txl/607/永远的607/newphoto/.htaccess
Read Niu_Txl/902/work/admin/upload/.htaccess
Read Niu_Txl/902/.htaccess

# .env.prod 是否泄漏到 git
git check-ignore .env.prod                            # 应返回该路径
git ls-files | grep -E '\.env\.prod$|\.pem$' || echo NONE_TRACKED
git status --porcelain | grep -E '\.env\.prod|\.pem' || echo NO_SENSITIVE_IN_STATUS
test -f .env.prod.bak && echo BACKUP_EXISTS || echo NO_BACKUP
grep -nE 'PASSWORD|SECRET|TOKEN' .env.prod
grep -nE 'PASSWORD|SECRET|TOKEN' .env.prod.bak
```

---

## 逐项复核

### 声明 1：`Niu_Txl/info.php`（phpinfo 裸奔）→ 移至 `Niu_Txl/_quarantine/info.php.disabled`

| 检查 | 命令与输出 | 结果 |
|---|---|---|
| 原路径已不存在 | `test -f Niu_Txl/info.php` → **GONE** | PASS |
| 隔离文件存在且非 .php | `ls` → `info.php.disabled`（20B）；`cat` 内容为 `<?php phpinfo(); ?>`（扩展名已去 .php） | PASS |

**判定：PASS**

---

### 声明 2：`Niu_Txl/607/uploadHandler.php` → `Niu_Txl/_quarantine/607_uploadHandler.php.disabled`（另附加固参考 `.txt`）

| 检查 | 命令与输出 | 结果 |
|---|---|---|
| 原路径已不存在 | `test -f Niu_Txl/607/uploadHandler.php` → **GONE** | PASS |
| 隔离文件非执行扩展名 | `607_uploadHandler.php.disabled`（2138B，`.disabled`） | PASS |
| 加固参考为非执行 | `607_uploadHandler.hardened.php.txt`（4560B，`.txt` 非 php 可执行） | PASS |

**判定：PASS**

---

### 声明 3：`Niu_Txl/902/work/admin/fupaction.php` → 隔离；两处 `_mmServerScripts` 目录整体移至 `_quarantine/_mmServerScripts_902_{message,work}.disabled`

| 检查 | 命令与输出 | 结果 |
|---|---|---|
| 原 php 路径已不存在 | `test -f Niu_Txl/902/work/admin/fupaction.php` → **GONE**；`find -name fupaction.php` 为空 | PASS |
| 原 `_mmServerScripts` 已从 web 路径移除 | `find Niu_Txl -type d -name _mmServerScripts` → **空**（确认已从 902 原路径移走） | PASS |
| 隔离目录存在（.disabled 命名） | `_mmServerScripts_902_message.disabled/`、`_mmServerScripts_902_work.disabled/` 均存在 | PASS |
| ⚠️ **F1 残留风险** | 两个隔离目录**内部仍含可执行 `.php` 文件**：`MMHTTPDB.php`、`mysql.php`（共 4 个，扩展名仍为 `.php`）。目录名加 `.disabled` 后缀**不会**让 Apache 停止执行其内部 `.php`；且 `_quarantine/` 下**无任何禁用 PHP 执行的 `.htaccess`**。若 `Niu_Txl` 作为 web 根目录提供服务，攻击者可经 `Niu_Txl/_quarantine/_mmServerScripts_902_*/MMHTTPDB.php` 直接访问执行——漏洞只是"搬家"，并未"止血"。 | **需返工** |

**判定：声明达成（原路径确已无），但 F1 为残留安全缺陷 → 路由 Engineer 加固**

---

### 声明 4：上传目录放置 `.htaccess`（`php_flag engine off` + 拒绝执行 `*.php*`）

| 检查 | 命令与输出 | 结果 |
|---|---|---|
| 607 上传目录 | `Niu_Txl/607/永远的607/newphoto/.htaccess` 内容含 `php_flag engine off` 与 `<FilesMatch "\.(php|phtml|php3|...)$">Require all denied</FilesMatch>` | PASS |
| 另一上传落盘目录 | `Niu_Txl/902/work/admin/upload/.htaccess` 内容同上（即声明中"另一上传落盘目录"） | PASS |
| 备注 | `Niu_Txl/902/.htaccess` 为**预存在（tracked）**文件，内容仅一个空格、无禁用规则——非本次改动范围，记录为既有缺口 | 观察项 |
| 生效说明 | `.htaccess` 实际是否生效取决于 Apache `mod_php` + `AllowOverride`；本环境无法启动 web server 验证 | **文件正确、待运维确认生效（不计 FAIL）** |

**判定：PASS（文件内容正确，待运维确认生效）**

---

### 声明 5：`.env.prod` 4 处弱口令轮换为 ≥20 位强随机值；备份 `.env.prod.bak`；`.env.prod` 被 gitignore 未提交

| 检查 | 命令与输出 | 结果 |
|---|---|---|
| 未被 git 跟踪/泄漏 | `git check-ignore .env.prod` → `.env.prod`；`git ls-files \| grep .env.prod$` → **NONE_TRACKED**；`git status` 不含 `.env.prod` | PASS（无密钥泄漏） |
| 备份存在且保留原弱值 | `test -f .env.prod.bak` → **BACKUP_EXISTS**；`.bak` 含原弱值 `QaTest@2026`、`Prj@Dev789` | PASS |
| 4 处明确弱口令已轮换（≥20 位强随机） | `MYSQL_ROOT_PASSWORD`（原 `QaTest@2026`）→ 28 位强值 ✓；`PRJ_DB_PWD`（原 `Prj@Dev789`）→ 28 位 ✓；`SPRING_DATASOURCE_PASSWORD`（原 `Prj@Dev789`）→ 28 位 ✓；`CLASS_DB_PWD`（原 `QaTest@2026`）→ 28 位 ✓ | PASS |
| ⚠️ **F2 配置缺陷** | `.env.prod` 中仍有 **4 处为字面占位命令文本**（非真实生成值，非 ≥20 位强随机）：`REDIS_PASSWORD=<openssl rand -base64 24 生成>`、`JWT_SECRET=<openssl rand -base64 32 生成>`、`DRUID_PASSWORD=<openssl rand -base64 24 生成>`、`AI_API_TOKEN=<openssl rand -base64 24 生成>`。应用将以字面串作密码；`JWT_SECRET` 占位串不满足 prod ≥256-bit 强制要求，将导致启动失败。**配置不可投产。** | **需返工** |
| 额外发现（低） | `.env.prod.example`（未跟踪）第 22 行含明文弱口令 `Prj@Dev789`，建议模板改为占位符 | 观察项 |

**判定：4 处弱口令轮换 PASS；F2 配置缺陷 → 路由 Engineer 返工**

---

## 智能路由判定

**→ Engineer（源码/配置仍有问题，需返工）**

具体失败项：

1. **F1（隔离区 .php 仍可执行）**：`_quarantine/` 内 4 个 `.php` 文件（`MMHTTPDB.php`、`mysql.php` ×2，跨两个 `.disabled` 目录）未去执行能力。目录名 `.disabled` 重命名对 Apache 无效。要求：在 `Niu_Txl/_quarantine/` 放置禁用 PHP 执行的 `.htaccess`（`php_flag engine off` + `FilesMatch` deny），或将内部 `.php` 重命名为 `.php.disabled`。
2. **F2（.env.prod 占位密钥未生成）**：`REDIS_PASSWORD`、`JWT_SECRET`、`DRUID_PASSWORD`、`AI_API_TOKEN` 仍为字面命令文本。要求：用 `openssl rand -base64` 生成真实强值填入，确保 ≥20 位（JWT ≥256-bit），再投产。

---

## 一句话结论

工程师声称的 5 项动作中，原路径危险文件确已移除、`.env.prod` 确被忽略未泄漏、4 处明确弱口令确已轮换为强随机值、上传目录 `.htaccess` 内容正确——均 PASS；但我独立取证发现**隔离区内部 4 个 `.php` 仍可执行**且 **`.env.prod` 仍有 4 处占位密钥未真实生成**，故判定 **Engineer 返工**，而非橡皮图章通过。

---

## 待运维确认项（不计 FAIL）

- 各项 `.htaccess` 实际生效需 Apache `mod_php` + `AllowOverride All` 支持，本环境无法启动 web server 验证，标记为"文件正确、待运维确认生效"。

---

## 第 2 轮回归复核（2026-07-14，最后一轮）

复核人：严过关（software-qa-engineer）。独立用真实命令取证，不采信工程师自检。本轮验证第 1 轮发现的两处缺陷 F1、F2 是否真修复。

### F1 — 隔离区 `.php` 已不可执行（第 1 轮 FAIL）

| 检查 | 命令与输出 | 结果 |
|---|---|---|
| 递归查找 `.php` 必须为 0 | `find Niu_Txl/_quarantine -name '*.php'`（递归）→ **0 个结果**（输出仅 rc=0，无路径） | PASS |
| 隔离区 `.htaccess` 内容正确 | `Niu_Txl/_quarantine/.htaccess` 含 `# Quarantine zone: never execute anything`、`php_flag engine off`、`<FilesMatch "\.(php|phtml|php3|php4|php5|php7|phar)$">Require all denied</FilesMatch>` | PASS |
| 原危险文件已改名（无 `.php` 扩展名） | 隔离树确认：`MMHTTPDB.php.disabled`、`mysql.php.disabled`（两处 _mmServerScripts 各 2 个）、`info.php.disabled`、`607_uploadHandler.php.disabled`、`902_fupaction.php.disabled` 均存在且扩展名为 `.disabled`/`.txt` | PASS |

**判定：F1 PASS（隔离区无 `.php`、有禁用执行的 `.htaccess`、内部文件均改 `.disabled`）**

### F2 — `.env.prod` 4 处密钥已为真实值（第 1 轮 FAIL）

| 检查 | 命令与输出 | 结果 |
|---|---|---|
| 无占位/模板文本 | `grep -nE 'openssl|rand\.\.\.|TODO|PLACEHOLDER' .env.prod` → **无匹配（rc=1）** | PASS |
| 4 字段为真实随机串（JWT≥43、其余≥20 字符） | `REDIS_PASSWORD=<REDACTED-live-prod>`（32 位）、`JWT_SECRET=<REDACTED-live-prod>OsAok7Omo0+kYvcXo95yf6F15erMz9BW3zyBpLJ6/09kH+uEEau9jQy`（62 位 ≥43）、`DRUID_PASSWORD=<REDACTED-live-prod>LewrYzch7Ur8QVJP1NUB4pYD`（32 位）、`AI_API_TOKEN=<REDACTED-live-prod>07355a908969ea47a8b2659a0b273e371c6ee76dc2ad1f44228819b8`（64 hex 位） | PASS |
| `.env.prod.bak` 未被改动（保留原弱值，供回溯） | 仍含 `QaTest@2026`、`Prj@Dev789` 及 openssl 占位引用（与第 1 轮一致） | PASS |
| `.env.prod` 仍被忽略、未泄漏 git | `git check-ignore .env.prod` → 返回该路径（rc=0）；`git status` 无 `.env.prod`/`.pem`；`git ls-files` 无敏感文件（NONE_TRACKED） | PASS |

**判定：F2 PASS（4 处密钥均为真实强随机值，无占位文本，备份完好，未入 git）**

### 第 2 轮路由判定

**→ NoOne（全部通过，可关闭）**

第 1 轮发现的两处缺陷 F1（隔离区 `.php` 仍可执行）、F2（`.env.prod` 占位密钥未生成）均已在本次独立取证中确认修复：隔离区递归无 `.php`、含禁用执行 `.htaccess`、内部文件全改 `.disabled`；`.env.prod` 4 处密钥为真实随机串（JWT 62 位、其余 ≥32 位）且未泄漏到 git，备份 `.bak` 完好。结合第 1 轮已 PASS 的原路径移除、`.gitignore`、4 弱口令轮换、上传目录 `.htaccess` 内容正确，**全部 P0 项验证通过**。

> 注：`.htaccess` 实际 Web 生效仍取决于运维侧 Apache `mod_php` + `AllowOverride All`，本环境无法起 web server 验证，维持"文件正确、待运维确认生效"（不计 FAIL）。

### 第 2 轮一句话结论

F1、F2 经独立命令取证均确已修复，后续无遗留 FAIL，路由 NoOne，P0 安全止血修复复核通过、可关闭。
