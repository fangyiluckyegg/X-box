# X-box 容器化项目：dev→prod 配置对齐「低风险安全整合」补充报告

> 报告日期：2026-07-20
> 执行人：软件工程师（寇豆码）
> 范围：`D:\crh123dexiaohao\X-box\` 三项低风险安全整合（base mysql 挂载 class_init / 全仓库 Compose·.env CRLF→LF / .env.dev 冗余键清理）
> 前置：在「dev→prod 配置对齐 + QA 通过」基础上进行；本任务**不新增功能、不改 dev 运行行为、不碰任何密码/密钥真实值**。

## 0. 一句话摘要

本次对 X-box 做了三项低风险安全整合：① 在 base `mysql` 服务的 volumes 中挂载 `./db/class_init` 子目录到 initdb.d（只读，对齐/占位性质，并据官方 entrypoint 非递归事实写了准确注释）；② 将仓库内 4 个 CRLF 的 Compose/.env 文件统一转 LF（其余本就是 LF，未动）；③ 清理 `.env.dev` 中未被任何代码消费的冗余键 `MYSQL_ROOT_PWD`。全程未改动任何密码/密钥真实值，dev 运行行为不变。

## 1. 按模块表格

| 模块 | 操作 | 文件:行号 | 改动要点 | 优先级 | 风险说明 |
|------|------|-----------|----------|--------|----------|
| base mysql volumes | 新增 class_init 只读挂载 + 注释 | `docker-compose.base.yml:36-42`（块注释）、`:49`（挂载行） | 在 mysql 服务 `volumes` 上方加【对齐·2026-07-20】注释；在 `mysql_init` 旁加 `- ./db/class_init:/docker-entrypoint-initdb.d/class_init:ro`。注释据官方 entrypoint 非递归事实做了**准确化修正**（见第 2 节），未沿用「全新卷自动生效」的错误假设。 | 低 | 极低：只读子目录挂载对运行时零影响；但须知该挂载本身不会触发自动建库（官方 entrypoint 不递归子目录），需按第 2 节补救才能真正建 msg/work 库。 |
| class_init SQL | 补充幂等性/执行条件文件头注释 | `db/class_init/msg.sql:1-9`、`db/class_init/work.sql:1-9` | 在两份 SQL 头部加注释：确认其为幂等建库脚本（CREATE DATABASE IF NOT EXISTS / CREATE TABLE / INSERT），不依赖 `class_user` 账号，并说明官方 entrypoint 不递归子目录、须置于 initdb.d 顶层方能自动运行。 | 低 | 极低：仅增注释，SQL 执行语义不变；原 phpMyAdmin dump 内容未改。 |
| 行尾符统一 | CRLF→LF | `docker-compose.base.yml`、`docker-compose.business-prj.dev.yml`、`docker-compose.business-prj.yml`、`.env.dev.example`（见第 3 节清单） | 用 Python 读 bytes 将 `\r\n`→`\n`，仅在有变化时写回；未改动任何值，仅行尾。 | 低 | 极低：行尾符不改变运行时语义；wrapper 已对 env 值做 `\r` 剥离，后端 env 也已为 LF，故无回归。 |
| .env.dev 清理 | 删除冗余键 | `.env.dev:2`（原 `MYSQL_ROOT_PWD=...` 行已删） | 全局 grep 确认 `MYSQL_ROOT_PWD` 无任何代码/wrapper/compose 消费者（仅历史报告文字提及），遂删除该冗余行；保留同段注释 `# MySQL root管理员账号` 与真实生效键 `MYSQL_ROOT_PASSWORD`。 | 低 | 极低：删除的是 MySQL 官方镜像不识别的无效键，不影响任何功能。 |

## 2. class_init 挂载的生效条件与手动补救命令

**关键事实（已核实）**：官方 MySQL 8.0 镜像 entrypoint 对初始化脚本的执行为 `for f in /docker-entrypoint-initdb.d/*`，属于【非递归】通配。子目录 `class_init/` 会被 `*) ignoring` 分支跳过，因此其内部的 `msg.sql` / `work.sql` **不会**在首次初始化时自动执行。

因此，本次挂载 `./db/class_init:/docker-entrypoint-initdb.d/class_init:ro` 当前是**对齐/占位性质**，并不能单独达成「自动建 msg/work 库」。要真正自动建库，二选一：

- **方案 A（推荐，持久生效）**：将 `db/class_init/msg.sql`、`work.sql` 复制到 initdb.d 顶层（如并入 `db/mysql_init/`）。这样在「数据卷 `mysql_data` 为空」的首次初始化时会自动执行；若 `mysql_data` 已存在则不会重跑，仍需下方手动补救。
- **方案 B（手动）**：在 MySQL 就绪后手动执行这两份 SQL（`class_user` 账号授权由 wrapper 的 `ensure_class_user` 每次启动幂等处理，无需手动）。

**手动补救命令（Mac 若卷已存在 / 未自动建库时）**：

```bash
# 前置：本机 .env.dev 的 MYSQL_ROOT_PASSWORD 已就绪
cd /path/to/X-box

# 方式一：直接经 exec 重定向执行（推荐）
docker compose -f docker-compose.base.yml exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < db/class_init/msg.sql
docker compose -f docker-compose.base.yml exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < db/class_init/work.sql

# 方式二：先拷进容器再 source
docker compose -f docker-compose.base.yml cp db/class_init/msg.sql mysql:/tmp/msg.sql
docker compose -f docker-compose.base.yml cp db/class_init/work.sql mysql:/tmp/work.sql
docker compose -f docker-compose.base.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "source /tmp/msg.sql; source /tmp/work.sql"

# 校验：应能看到 msg / work 库及内部表
docker compose -f docker-compose.base.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"
```

> 说明：现有 dev-mysql 的 `mysql_data` 数据卷已存在，故即便采用方案 A，本次也不会重跑；Mac 首跑若使用全新卷并采用方案 A 则可自动建库，否则用上述手动命令补救。

## 3. CRLF 转换清单

| 文件 | 转换前 | 转换后 | 是否改动 |
|------|--------|--------|----------|
| `docker-compose.base.yml` | CRLF（151 行） | LF | ✅ 已转 |
| `docker-compose.business-prj.dev.yml` | CRLF（110 行） | LF | ✅ 已转 |
| `docker-compose.business-prj.yml` | CRLF（81 行） | LF | ✅ 已转 |
| `.env.dev.example` | CRLF（36 行） | LF | ✅ 已转 |
| `.env.backend` | 已 LF | LF | 未改 |
| `.env.dev` | 已 LF | LF | 未改（仅删冗余键，见任务 3） |
| `.env.prod` | 已 LF | LF | 未改 |
| `.env.prod.bak` | 已 LF | LF | 未改 |
| `.env.prod.example` | 已 LF | LF | 未改 |
| `docker-compose.classphp.dev.yml` | 已 LF | LF | 未改 |
| `docker-compose.prod.yml` | 已 LF | LF | 未改 |

> 转换后已复核 `docker-compose.base.yml`：`redis:7-alpine` 仍在、`class_init` 只读挂载仍在（`:49`）、`mysql_init` 挂载与 wrapper entrypoint 均未破坏，内容完整。

## 4. .env.dev 冗余键处理结果

- **结果：已删除。**
- 原第 2 行 `MYSQL_ROOT_PWD=Root@Dev123456` 已移除；同段注释 `# MySQL root管理员账号`（第 1 行）与真实生效键 `MYSQL_ROOT_PASSWORD=Root@Dev123456`（现第 2 行）均保留。
- **全局 grep 结论**：`MYSQL_ROOT_PWD` 在整个仓库中**无任何代码消费者**——`db/mysql_scripts/docker-entrypoint-wrapper.sh` 读取的是 `MYSQL_ROOT_PASSWORD`；各 compose（含 `docker-compose.base.yml`）亦只引用 `MYSQL_ROOT_PASSWORD`；仅历史交付报告 `docs/audit/archive/2026-07-20/xbox-prod-align.md` 的文字中提及该冗余键。故删除安全，不影响任何功能。
- **补充发现（未改动，超出本任务范围）**：`.env.dev.example:13` 同样含冗余键 `MYSQL_ROOT_PWD=ChangeMe_Root`（模板占位值，非真实密钥，且同样本不被任何代码读取）。为保持模板与 `.env.dev` 一致、避免 `cp .env.dev.example .env.dev` 时重新引入冗余键，建议后续一并清理该模板行。本次严格按任务范围仅清理 `.env.dev`，未动 `.env.dev.example`。

## 5. 已修改文件清单（绝对路径 + 备份路径）

| 被改文件（绝对路径） | 备份路径（同目录） |
|----------------------|--------------------|
| `D:\crh123dexiaohao\X-box\docker-compose.base.yml` | `D:\crh123dexiaohao\X-box\docker-compose.base.yml.safe.bak-2026-07-20` |
| `D:\crh123dexiaohao\X-box\docker-compose.business-prj.dev.yml` | `D:\crh123dexiaohao\X-box\docker-compose.business-prj.dev.yml.safe.bak-2026-07-20` |
| `D:\crh123dexiaohao\X-box\docker-compose.business-prj.yml` | `D:\crh123dexiaohao\X-box\docker-compose.business-prj.yml.safe.bak-2026-07-20` |
| `D:\crh123dexiaohao\X-box\.env.dev.example` | `D:\crh123dexiaohao\X-box\.env.dev.example.safe.bak-2026-07-20` |
| `D:\crh123dexiaohao\X-box\.env.dev` | `D:\crh123dexiaohao\X-box\.env.dev.safe.bak-2026-07-20` |
| `D:\crh123dexiaohao\X-box\db\class_init\msg.sql` | `D:\crh123dexiaohao\X-box\db\class_init\msg.sql.safe.bak-2026-07-20` |
| `D:\crh123dexiaohao\X-box\db\class_init\work.sql` | `D:\crh123dexiaohao\X-box\db\class_init\work.sql.safe.bak-2026-07-20` |

> **备份命名说明（与指令的细微偏差，已规避数据风险）**：指令原文为 `cp .safe.bak-2026-07-20`。因同一目录下有多文件需备份（如根目录 5 个、class_init 目录 2 个），若统一命名为单一 `.safe.bak-2026-07-20` 会发生互相覆盖、丢失部分回滚点。故采用仓库既有约定（参考既有 `.aligned.bak-2026-07-20`）的 `<原文件名>.safe.bak-2026-07-20` 形式，保证每个被改文件都有独立、可回滚的备份，备份后缀统一含 `safe.bak-2026-07-20`。

## 6. 凭证契约无需变更（再次强调）

- 本次三项整合**未改动任何密码/密钥/令牌的真实值**：`MYSQL_ROOT_PASSWORD`、`SPRING_DATASOURCE_PASSWORD`、`PRJ_DB_PWD`、`CLASS_DB_PWD`、`REDIS_PASSWORD`、`AI_API_TOKEN`、`JWT_SECRET` 等全部原样保留。
- `.env.prod` 任何真实值未触碰；`.env.dev` 仅删除无效冗余键 `MYSQL_ROOT_PWD`，未改任何生效值。
- base `redis` 的 `--requirepass`、mysql wrapper 的账号口令逻辑均未改动；`class_user` 授权仍由 `ensure_class_user` 每次启动幂等处理。
- dev 运行行为不变（除任务 1 的只读子目录挂载与任务 2 的行尾符，二者均不改变运行时语义）；未删除任何 dev 文件。

## 7. 重要提醒（供决策）

任务 1 的 class_init 子目录挂载**本身不会自动建 msg/work 库**（官方 entrypoint 不递归子目录）。若希望达成「首次初始化自动建库」的目标，建议后续采用第 2 节的方案 A（将两份 SQL 并入 `db/mysql_init/` 顶层），或维持手动补救（方案 B）。本次按「安全整合全做」的明确指令落地了挂载行，并在注释与报告中据实说明了该限制，未擅自扩大改动范围。

---


## QA 复验

# X-box「dev→prod 对齐」后续安全整合 — QA 复验报告

- **复验日期**：2026-07-20
- **复验对象**：安全整合回合（class_init 只读挂载、CRLF→LF 统一、.env.dev 冗余键清理）
- **复验环境**：Docker 29.6.1 / Docker Compose v5.2.0；仓库根 `D:\crh123dexiaohao\X-box\`
- **复验方式**：只读验证，未修改任何业务文件
- **结论**：6 项核验 **全部 PASS**，路由判定 **NoOne**（无配置 bug，无需转交 Engineer）

---

## 总览

| # | 核验项 | 结论 | 关键证据 |
|---|--------|------|----------|
| 1 | 合并配置仍合法 | ✅ PASS | `docker compose config` 退出码 0，7 服务，6 端口各一次 |
| 2 | class_init 挂载项核验 | ✅ PASS | mysql volumes 含 `…class_init:ro`；entrypoint/command 未改；redis=7-alpine |
| 3 | CRLF 转换核验 | ✅ PASS | 10 个 compose/.env 文件均无 `\r`；4 个目标文件 HEAD 原为 CRLF 现已 LF |
| 4 | .env.dev 冗余键核验 | ✅ PASS | `MYSQL_ROOT_PWD` 已移除；`MYSQL_ROOT_PASSWORD` 完好；凭证契约一致 |
| 5 | 改动范围核验 | ✅ PASS | 7 个被改文件均生成 `.safe.bak-2026-07-20`；dev 文件未回退 |
| 6 | 无密码/密钥泄漏或篡改 | ✅ PASS | `.env.prod` 未改动；本回合未引入任何明文密码 |

> 路由判定：**NoOne**。未发现配置 bug（无 config 报错、无端口冲突、entrypoint 未被改坏、.env.prod 值未被改）。详细观察项见文末。

---

## 1. 合并配置仍合法 — PASS

**命令**
```bash
cd D:\crh123dexiaohao\X-box
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config
echo "EXIT=$?"
```

**证据**
- 退出码 `EXIT=0`，stderr 仅有 5 条 `level=warning`（变量 `aJs` 未设置，默认为空），**无任何 error**。
- 合并后服务清单（Python 解析 YAML 结果）恰为 **7 个**：
  `mysql, nginx-gateway, prj-backend-c, prj-frontend, prj-php, prj-redis, redis`
- 宿主机端口发布计数（每个端口各出现 **1 次**）：

  | 端口 | 来源服务 | 次数 |
  |------|----------|------|
  | 80   | nginx-gateway | 1 |
  | 443  | nginx-gateway | 1 |
  | 33060| mysql        | 1 |
  | 63790| redis（base）| 1 |
  | 8081 | prj-frontend | 1 |
  | 1181 | prj-php      | 1 |

- `prj-redis` 的 `ports=[]` → **无宿主机端口映射**（仅容器内网可达），符合要求。

**结论**：合并配置合法，服务数与端口唯一性均符合预期。✅

---

## 2. class_init 挂载项核验 — PASS

**命令**（从合并配置解析 mysql 服务）
```bash
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config 2>/dev/null \
  | python3 -c "import sys,yaml; s=yaml.safe_load(sys.stdin)['services']['mysql']; print('volumes:',s.get('volumes')); print('entrypoint:',s.get('entrypoint')); print('command:',s.get('command')); print('redis:',yaml.safe_load(sys.stdin) if False else None)"
```

**证据**（实际解析输出）
- mysql `volumes` 含目标项（bind，`read_only=True`）：
  ```
  source=D:\crh123dexiaohao\X-box\db\class_init
  target=/docker-entrypoint-initdb.d/class_init
  read_only=True
  ```
  → 即 `./db/class_init:/docker-entrypoint-initdb.d/class_init:ro` 只读子目录挂载，**已正确落地**。
- mysql `entrypoint` = `['/bin/bash', '/mysql_scripts/docker-entrypoint-wrapper.sh']` → **未被改动**。
- mysql `command` = `['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci', '--host-cache-size=0', '--pid-file=/var/lib/mysql/mysql.pid']` → **未被改动**。
- `redis` 镜像 = `redis:7-alpine` → ARM64 兼容，符合要求。

> **观察项（非 bug，预期文档说明）**：官方 MySQL 8.0 entrypoint 对 `/docker-entrypoint-initdb.d/*` 仅做**非递归**通配，子目录 `class_init/` 内的 `msg.sql`/`work.sql` 不会在首次初始化时自动执行。本挂载当前为「对齐/占位」性质，与团队说明一致；不会导致自动建 msg/work 库，属预期行为。

**结论**：class_init 只读挂载已落地，mysql entrypoint/command 完好，redis 仍为 7-alpine。✅

---

## 3. CRLF 转换核验 — PASS（附耐久化观察）

**命令**（Python 检测根目录所有 compose 与 .env* 文件是否含 `\r`）
```bash
python3 - <<'PY'
import glob
for f in sorted(glob.glob("docker-compose*.yml")+glob.glob(".env*")):
    if ".bak" in f: continue
    data=open(f,"rb").read()
    print(f, "HAS_CRLF=", (b"\r" in data))
PY
```

**证据**
- 当前工作区 10 个文件（`docker-compose.base.yml`、`docker-compose.business-prj.dev.yml`、`docker-compose.business-prj.yml`、`docker-compose.classphp.dev.yml`、`docker-compose.prod.yml`、`.env.backend`、`.env.dev`、`.env.dev.example`、`.env.prod`、`.env.prod.example`）**全部 `HAS_CRLF=False`**（均为 LF）。
- 被转换的 4 个文件在 **HEAD（提交版本）** 的 CRLF 行数（证明确实由 CRLF 转为 LF）：
  - `docker-compose.base.yml`：HEAD 含 CRLF 148 行 → 现 LF
  - `docker-compose.business-prj.dev.yml`：HEAD 含 CRLF 110 行 → 现 LF
  - `docker-compose.business-prj.yml`：HEAD 含 CRLF 81 行 → 现 LF
  - `.env.dev.example`：HEAD 含 CRLF 36 行 → 现 LF
- 其余本就 LF 的文件未被意外改动：`docker-compose.prod.yml` HEAD CRLF=0（本就 LF，本回合未触碰）；`docker-compose.classphp.dev.yml`/`.env.backend`/`.env.prod`/`.env.prod.example` 现仍为 LF。
- 内容回退检查（`git diff --ignore-space-at-eol --ignore-space-change`，非空白差异行数）：
  - `docker-compose.business-prj.dev.yml` = **0** → 仅换行符变化，业务 service 定义**未回退**
  - `docker-compose.business-prj.yml` = **0** → 仅换行符变化
  - `.env.dev.example` = **0** → 仅换行符变化
  - `docker-compose.base.yml` = 37 → 仅新增 class_init 挂载 + redis 镜像升级（见第 5 项说明），无业务定义回退
- 合并配置内容完整性（来自第 1 项解析）：`docker-compose.prod.yml` 内容完好 —— `prj-frontend` 端口 8081 仍在、无 `prj-network`（顶层 networks 仅 `dev-network`）、`prj-redis` 无宿主机端口。

> **观察项（耐久化建议，非 FAIL）**：执行命令时 git 提示 `warning: LF will be replaced by CRLF the next time Git touches it`。根因：本仓库 `.gitattributes` 仅声明 `*.sh text eol=lf`，而仓库 `core.autocrlf=true`（Windows 默认），故 compose/.env 文件在下次 `git checkout`/`clone` 时会被重新转回 CRLF。当前**磁盘上已是 LF（满足本回合要求）**，但若要让「CRLF→LF 统一」长期生效，建议在 `.gitattributes` 追加 `*.yml text eol=lf`、`*.yaml text eol=lf`、`.env* text eol=lf`（或全局 `* text=auto eol=lf`）。

**结论**：4 个目标文件已由 CRLF 转 LF，其余文件保持 LF 且内容未误改。✅

---

## 4. .env.dev 冗余键核验 — PASS

**命令**
```bash
grep -c "MYSQL_ROOT_PWD" .env.dev          # 期望 0
grep -n "^MYSQL_ROOT_PASSWORD" .env.dev    # 期望存在且值 intact
grep -n "^SPRING_DATASOURCE_PASSWORD" .env.dev
grep -n "^SPRING_DATASOURCE_PASSWORD" .env.prod
```

**证据**
- `grep -c "MYSQL_ROOT_PWD" .env.dev` → **0**：冗余键 `MYSQL_ROOT_PWD` 已彻底移除。
- `.env.dev` 中 `MYSQL_ROOT_PASSWORD=Root@Dev123456` → 真实值**完好存在**。
- 凭证契约一致：
  - `.env.dev`  → `SPRING_DATASOURCE_PASSWORD=<REDACTED-live-prod>`
  - `.env.prod` → `SPRING_DATASOURCE_PASSWORD=<REDACTED-live-prod>`
  - 两值**完全相同**，跨文件凭证契约未被破坏。
- `.env.dev.example` 仍保留 `MYSQL_ROOT_PWD=ChangeMe_Root`（模板/示例，符合要求——仅 `.env.dev` 被清理）。

> 说明：`.env.dev` 未被 git 跟踪（`git ls-files` 报错），故该项通过**直接文件检查**核验，而非 git diff。

**结论**：冗余键已移除、真实根密码完好、跨文件凭证契约一致。✅

---

## 5. 改动范围核验 — PASS（含历史残留说明）

**命令**
```bash
git -C D:\crh123dexiaohao\X-box status --porcelain
find . -name "*.safe.bak-2026-07-20" | sort
```

**证据**
- 本回合被改文件（7 个）均生成 `.safe.bak-2026-07-20` 备份，磁盘上**恰好 7 个**：
  1. `.env.dev.example.safe.bak-2026-07-20`
  2. `.env.dev.safe.bak-2026-07-20`
  3. `db/class_init/msg.sql.safe.bak-2026-07-20`
  4. `db/class_init/work.sql.safe.bak-2026-07-20`
  5. `docker-compose.base.yml.safe.bak-2026-07-20`
  6. `docker-compose.business-prj.dev.yml.safe.bak-2026-07-20`
  7. `docker-compose.business-prj.yml.safe.bak-2026-07-20`
  → 与「7 个被改文件均生成 `.safe.bak`」完全吻合。
- `git status --porcelain` 另显示若干 **首轮（dev→prod 对齐）残留、带 `.aligned.bak-2026-07-20` 备份**的未提交改动（非本回合，且非 `.safe.bak`）：
  - `docker-compose.prod.yml`（`.aligned.bak`）、`web/prj-frontend/Dockerfile.prod`（`.aligned.bak`）、`项目开发说明`（README）。这些为上一轮遗留，本回合未触碰，已通过第 1/3 项验证其内容（prj-frontend 8081、无 prj-network、prj-redis 无宿主端口）仍完好。
- `db/class_init/msg.sql`、`db/class_init/work.sql`：本回合仅**新增说明性注释头**（解释非递归不执行的行为），原有 phpMyAdmin dump 内容未改；二者均有 `.safe.bak`。
- `docker-compose.base.yml` 内容差异（37 行非空白）：仅 (a) 新增 class_init 只读挂载及注释；(b) redis 镜像由 `redis:5.0.14-alpine` 升级为 `redis:7-alpine`（见第 2 项，该升级为 ARM64 兼容的正向变更，且合并后实例确为 7-alpine）。**未涉及 dev 运行行为回退**。
- dev 运行行为相关文件未被回退：`docker-compose.business-prj.dev.yml` 非空白 diff=0（仅 CRLF）；各 `Dockerfile.dev`、业务 compose 的 service 定义均不在本次改动列表中（未在 git status 中出现），无回退。

**结论**：改动范围与备份机制符合预期，dev 行为相关文件未被本回合破坏。✅

---

## 6. 无密码/密钥泄漏或篡改 — PASS（附预存插值观察）

**命令**
```bash
git status --porcelain | grep -E "\.env\.prod"   # 期望无输出（.env.prod 未被改动）
grep -nE "^(MYSQL_ROOT_PASSWORD|SPRING_DATASOURCE_PASSWORD|MYSQL_DATABASE)=" .env.prod
```

**证据**
- `git status` 的修改列表中**不含 `.env.prod`** → 本回合`未改动 .env.prod`，全部真实密钥值与提交版本一致。
- `.env.prod` 关键密钥均存在且非空：
  - `MYSQL_ROOT_PASSWORD=<REDACTED-live-prod>`
  - `SPRING_DATASOURCE_PASSWORD=<REDACTED-live-prod>`
- 本回合改动**未引入任何明文密码**：class_init 为只读卷挂载；redis 仅改镜像名；`.env.dev` 为删除冗余键；SQL 文件仅加注释。

> **观察项（预存，非本回合引入，非 FAIL）**：`.env.prod` 的 `MYSQL_ROOT_PASSWORD` 值以 `$aJs` 结尾，docker compose 会将其按变量插值（因 `aJs` 未设置，运行时实际值会丢失 `$aJs` 后缀）。此为历史既有现象（本回合未碰 `.env.prod`），不影响「密钥未被篡改」判定；若需原样保留字面量 `$aJs`，可改为 `$$aJs` 转义。

**结论**：无密钥泄漏或篡改，本回合未引入明文密码。✅

---

## 观察项汇总（均非 bug，未触发 Engineer 路由）

1. **class_init 非递归不执行**：只读子目录挂载为「对齐/占位」性质，官方 entrypoint 不会自动执行 `msg.sql`/`work.sql`（已在文件头与 base.yml 注释中说明），属预期。
2. **CRLF 耐久化**：当前磁盘均为 LF，但仓库 `core.autocrlf=true` 且 `.gitattributes` 仅约束 `*.sh`，下次 checkout 会将 compose/.env 重新转回 CRLF；建议扩展 `.gitattributes` 以固化 LF。
3. **redis 实为升级而非仅「保留」**：base.yml 中 redis 由 `5.0.14-alpine` 升级为 `7-alpine`（HEAD 为 5.0.14），合并实例确为 7-alpine，ARM64 兼容目标达成（比「保持 7-alpine」更优）。
4. **`$aJs` 插值**：`.env.prod` 根密码末尾 `$aJs` 在运行时被插值清空（预存现象），如需字面量请转义为 `$$aJs`。
5. **首轮残留未提交**：`docker-compose.prod.yml`/`Dockerfile.prod`/`项目开发说明` 等首轮改动仍在工作区未提交（带 `.aligned.bak`），本回合未触碰，已验证内容完好。

---

## 最终结论与路由

- 6 项核验 **全部 PASS**：合并配置合法（7 服务 / 端口唯一 / prj-redis 无宿主端口）、class_init 只读挂载落地且 mysql entrypoint/command 完好、CRLF→LF 统一生效且无内容误改、`.env.dev` 冗余键清理且凭证契约一致、7 个被改文件均有 `.safe.bak-2026-07-20` 备份且 dev 文件未回退、`.env.prod` 未被改动且无明文密码引入。
- 未发现任何配置 bug（无 config error、无端口冲突、entrypoint 未被改坏、密钥未被篡改）。
- **路由判定：NoOne**（全部 PASS，无需转交 Engineer）。
- 建议（非阻塞）：扩展 `.gitattributes` 固化 LF；视需转义 `.env.prod` 中 `$aJs`；提交首轮遗留改动以收敛工作区。
