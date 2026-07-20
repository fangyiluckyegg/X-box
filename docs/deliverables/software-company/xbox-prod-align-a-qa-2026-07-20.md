# X-box 容器化 · 方案 A 独立复验 QA 报告

- **验证日期**：2026-07-20
- **验证人**：Edward（QA 工程师）
- **验证对象**：方案 A「真修复」——把 `db/class_init/` 的 `msg.sql`、`work.sql` 并入 `db/mysql_init/` 顶层，使 MySQL 全新数据卷首次初始化自动建 `msg`/`work` 库；移除 base 中无效的 `class_init` 子目录挂载；删除孤立的 `db/class_init/` 目录。
- **验证性质**：只读复验（未修改任何业务文件，仅运行校验命令与脚本）。
- **环境**：Docker 29.6.1（本机可用）；项目根 `D:\crh123dexiaohao\X-box`。

---

## 验证命令（主）

```
cd D:\crh123dexiaohao\X-box
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config
```

---

## 一、逐项结果（PASS / FAIL + 证据）

### ① 合并配置仍合法 — ✅ PASS

**命令与退出码**
```
$ docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config > /tmp/compose_config.txt 2>/tmp/compose_config.err
$ echo EXIT_CODE=$?
EXIT_CODE=0
```
- `config` 退出码为 **0**，配置合法；仅 `stderr` 出现 5 条 `level=warning msg="The \"aJs\" variable is not set..."` 告警（**非 error**，为历史遗留，详见观察项 O1），不影响合法性。

**合并后服务清单（恰为 7 个）**
```
mysql  nginx-gateway  prj-backend-c  prj-frontend  prj-php  prj-redis  redis
```
（提取自 `services:` 下一级键，排除 `networks:`/`volumes:` 的 `dev-network`、`mysql_data`。）

**宿主发布端口（80/443/33060/63790/8081/1181 各仅一次）**
```
published: "1181"   x1
published: "33060"  x1
published: "443"    x1
published: "63790"  x1
published: "80"     x1
published: "8081"   x1
```
**prj-redis 无宿主端口映射**：在合并配置中 `prj-redis` 段仅含 `target: /data`、`target: /var/log/redis`（来自 volume 挂载），无任何 `published` 行。✅

---

### ② class_init 挂载已移除、mysql/redis 配置完好 — ✅ PASS

**证据 A：与方案 A 前备份逐行 diff，证明 base.yml 改动仅此一处**
```
$ diff docker-compose.base.yml docker-compose.base.yml.a.bak-2026-07-20
36,42c36,42
<     # 【方案A·2026-07-20】移除无效的 class_init 子目录挂载（原行：
<     #   - ./db/class_init:/docker-entrypoint-initdb.d/class_init:ro）。
...
49,50c49
<       # 【方案A·2026-07-20】已移除 class_init 只读子目录挂载（死挂载，见上方说明）；
<       # msg/work SQL 现已并入 db/mysql_init/ 顶层，全新数据卷首次初始化即自动执行。
---
>       - ./db/class_init:/docker-entrypoint-initdb.d/class_init:ro  # [对齐·2026-07-20] class_init 子目录(只读)...
```
→ 差异**仅为删除** `- ./db/class_init:/docker-entrypoint-initdb.d/class_init:ro` 这一挂载行 + 注释更新，无其他改动。

**证据 B：在全仓 compose 中检索"活动"挂载（非注释）**
```
$ grep -rnE 'class_init' docker-compose*.yml | grep -v '\.bak' | grep -vE '^\s*#'
docker-compose.base.yml:36:    # 【方案A·2026-07-20】移除无效的 class_init 子目录挂载（原行：
docker-compose.base.yml:37:    #   - ./db/class_init:/docker-entrypoint-initdb.d/class_init:ro）。
docker-compose.base.yml:39:    #       子目录 class_init/ 内的 .sql 会被 "ignoring" 分支跳过...
docker-compose.base.yml:40:    # 真修复：db/class_init/msg.sql、work.sql 已并入 db/mysql_init/ 顶层...
docker-compose.base.yml:42:    # 原 db/class_init/ 目录已删除...
docker-compose.base.yml:49:      # 【方案A·2026-07-20】已移除 class_init 只读子目录挂载...
```
→ 命中行**全部为注释**（说明性文字），**无任何活动 bind 挂载**。合并后有效配置中 `class_init` 出现次数 = **0**（`grep -c "class_init" /tmp/compose_config.txt` → `0`）。

**mysql 其余配置完好（合并配置核对）**
- `entrypoint`：`/bin/bash` → `/mysql_scripts/docker-entrypoint-wrapper.sh`（保持不变）
- `command`：`--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci --host-cache-size=0 --pid-file=/var/lib/mysql/mysql.pid`（保持不变）
- 其余 mysql volumes（`mysql_data`、`/mysql_scripts`、`/var/log/mysql`）完好。

**redis 仍为 `redis:7-alpine`**
```
$ grep -nE 'image: redis' docker-compose.base.yml
85:    image: redis:7-alpine
```
✅（ARM64 兼容，未回退到 redis:5）。

---

### ③ mysql_init 顶层新增文件核验 — ✅ PASS

**文件存在性**
```
db/mysql_init/msg.sql   (5929 字节, 2026-07-20 15:05)
db/mysql_init/work.sql  (4561 字节, 2026-07-20 15:05)
```
`mysql_init/` 目录现有：`init.sql / init.template.sql / migrate_role.sql / msg.sql / work.sql`。

**关键语句（Read 实地确认）**
- `msg.sql` 第 37 行：`CREATE DATABASE IF NOT EXISTS \`msg\` DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;`
  - 第 46 / 64 / 91 行：`CREATE TABLE IF NOT EXISTS \`admin_user\`` / `\`post\`` / `\`reply\``
- `work.sql` 第 37 行：`CREATE DATABASE IF NOT EXISTS \`work\` ...`
  - 第 46 / 64 / 96 行：`CREATE TABLE IF NOT EXISTS \`admin_user\`` / `\`work_pic\`` / `\`work_type\``

→ **CREATE DATABASE 与 CREATE TABLE 均已补 `IF NOT EXISTS`，幂等正确**；与原文 phpMyAdmin dump 语义（表结构、INSERT、AUTO_INCREMENT）一致，仅新增幂等关键字。

**文件头方案 A 注释**
- `msg.sql` 第 1–15 行含 `【方案A·2026-07-20】原 db/class_init/msg.sql 并入 mysql_init 顶层...`
- `work.sql` 第 1–15 行含 `【方案A·2026-07-20】原 db/class_init/work.sql 并入 mysql_init 顶层...`

**全新卷自动执行保障**
- mysql 挂载 `./db/mysql_init:/docker-entrypoint-initdb.d`（合并配置已确认）。
- `msg.sql`/`work.sql` 现位于 `initdb.d` **顶层**（官方 entrypoint 对顶层 `*` 非递归通配会执行），故**全新数据卷首次初始化会自动建 `msg`/`work` 库**，不再依赖手动执行或子目录（子目录会被 "ignoring" 跳过，这正是方案 A 要修的死挂载问题）。✅

---

### ④ class_init 目录已删除、全仓无残留活动引用 — ✅ PASS

**目录删除确认**
```
$ test -d db/class_init && echo EXISTS || echo "NOT EXISTS (removed)"
NOT EXISTS (removed)
```
`git status --porcelain` 中显示 `D db/class_init/msg.sql`、`D db/class_init/work.sql`（已删除）。

**全仓 Grep `class_init` 结果归类**
| 命中位置 | 性质 | 是否计入"配置引用" |
|---|---|---|
| `db/mysql_init/msg.sql`、`work.sql`（文件头注释） | 来源说明文字 | 否（注释） |
| `docker-compose.base.yml`（第 36–42、49 行注释） | 来源/修复说明文字 | 否（注释） |
| `docker-compose.base.yml.a.bak-2026-07-20`（备份） | 历史备份副本 | 否（备份） |
| `deliverables/software-company/*.md`（5 份历史交付报告） | 历史报告文字 | 否（按约束排除） |
| `项目开发说明`、`docs/architecture_review.md`、`docs/X-box-optimization-report-2026-07-14.md`、`docs/prod-mac-runbook.md` | 文档/运行手册文字 | 否（文档文字；`prod-mac-runbook.md` 见观察项 O3） |

→ **无任何 compose 文件含 `class_init` 活动 bind 挂载**；有效合并配置中 `class_init` 引用数 = 0。✅

---

### ⑤ CRLF / 幂等影响核验 — ✅ PASS

**Python 字节级检测（`\r` 检测）**
```
db/mysql_init/msg.sql   : CRLF_present=False  LF_lines=140  CRLF_pairs=0
db/mysql_init/work.sql  : CRLF_present=False  LF_lines=142  CRLF_pairs=0
docker-compose.base.yml : CRLF_present=False  LF_lines=160  CRLF_pairs=0
```
→ 方案 A 新增/改动的三份文件**均为 LF**，与仓库统一（无 CRLF 行尾问题）。

> 注：`db/mysql_init/init.template.sql`、`migrate_role.sql` 仍为 CRLF（各 30/28 对），属**历史预存文件、非方案 A 改动**，作为观察项 O2 记录，不计入方案 A 判定。

**幂等性**：新建 `msg.sql`/`work.sql` 的 `CREATE TABLE` 全部带 `IF NOT EXISTS`，全新卷首次执行不会因"表已存在"报 1050 中断；已存在数据卷（`mysql_data`）不会重跑 `initdb.d`，对运行时无影响。✅

---

### ⑥ 改动范围核验 — ✅ PASS（附观察项 O4）

**方案 A 实际改动范围（隔离证明）**
- `docker-compose.base.yml`：与 `.a.bak` 逐行 diff 证明**仅删除 class_init 挂载行 + 注释更新**，别无其他。
- `db/mysql_init/msg.sql`、`db/mysql_init/work.sql`：新增（untracked，`??`）。
- `db/class_init/`：删除（`D`）。

→ **方案 A 的 delta 干净，仅限声明的 4 项。**

**`.env.prod` / `.env.dev` 真实密码未变**
- `.gitignore` 第 3 行 `.env*` 已忽略 `.env.prod`、`.env.dev`（`git check-ignore` 确认；`git ls-files` 仅跟踪 `.env.dev.example`/`.env.prod.example`）。
- `git status --porcelain` 中**不含** `.env.prod`/`.env.dev`（gitignored 且未被改动）。
- 方案 A 的 base.yml 改动（.a.bak diff）未触碰任何 env 变量或密码注入逻辑；`${CLASS_DB_PWD}`/`${REDIS_PASSWORD}` 仍为 compose 插值引用，无明文。
- 说明：`.env.prod` 与旧备份 `.env.prod.bak`（2026-07-14）的密码行"不同"，但 `.env.prod.bak` 本身是**占位模板**（`MYSQL_ROOT_PASSWORD=QaTest@2026`、`REDIS_PASSWORD=<openssl rand...>` 等），并非真实值快照；该差异是"占位 vs 真实"的正常现象，**非方案 A 篡改**。✅

**dev 运行相关文件未回退**
- `docker-compose.business-prj.dev.yml` 等虽在 `git status` 中显示为 `M`，但其对应 `*.aligned.bak-2026-07-20` / `*.safe.bak-2026-07-20` 备份时间为 **13:39**，早于方案 A 落地时间（15:05–15:07），属**早前"对齐"工作的未提交改动，非方案 A 引入**；方案 A 从未编辑这些文件，故不存在"回退"。合并 `config` 仍合法（见①），证明这些文件当前状态有效。✅

**备份存在**
```
$ ls -la docker-compose.base.yml.a.bak-2026-07-20
-rw-r--r--  ... 7717 ... docker-compose.base.yml.a.bak-2026-07-20   ✅
```

**观察项 O4（建议主理人关注，非方案 A 缺陷）**
`git status` 工作区除方案 A 的 4 项外，还含早前对齐的未提交改动：
```
 M .env.dev.example
 M docker-compose.business-prj.dev.yml
 M docker-compose.business-prj.yml
 M docker-compose.prod.yml
 M web/prj-frontend/Dockerfile.prod
 M "项目开发说明"
?? deliverables/software-company/*.md (5 份)
?? docker-compose.*.aligned.bak-2026-07-20 / *.safe.bak-2026-07-20 (多份)
```
这些均非方案 A 引入（有 13:39 的 aligned/safe 备份佐证），但建议主理人确认是否应随方案 A 一并纳入本次验收/提交，避免工作区长期堆积未提交改动。

---

### ⑦ 无密码 / 密钥泄漏或篡改 — ✅ PASS

- 方案 A 三份改动文件（base.yml diff、msg.sql、work.sql）均**未引入任何明文密码/密钥**；`.env.prod`/`.env.dev` 未被改动（见⑥）。
- `.env.dev.example` 仍为占位值（`MYSQL_ROOT_PASSWORD=ChangeMe_Root`、`SPRING_DATASOURCE_PASSWORD=ChangeMe_Prj` 等），无真实凭据泄漏。✅

---

## 二、观察项（非方案 A bug，不路由工程师）

- **O1（预存）**：`docker compose config` 的 `stderr` 出现 5 条 `The "aJs" variable is not set` 告警。根因：`.env.prod` 的 `MYSQL_ROOT_PASSWORD=<REDACTED-live-prod>` 含 `$aJs`，被 compose 当作变量插值。此为**历史预存**问题，非方案 A 引入；仅告警、不阻断 config（退出码 0）。建议后续由工程师评估是否对 `.env.prod` 密码中的 `$` 转义，但与本次验收无关。
- **O2（预存）**：`db/mysql_init/init.template.sql`、`migrate_role.sql` 仍为 CRLF；`init.template.sql` 历史上有"全新卷重复 CREATE TABLE 报 1050 但官方 entrypoint 不中断循环"的现象（按约束作为观察记录，误判为方案 A bug）。非方案 A 改动。
- **O3（预存文档）**：`docs/prod-mac-runbook.md` 仍文字引用 `class_init`（历史运行手册）。按约束**不擅自改文档**，仅作观察记录。
- **O4（工作区状态）**：见⑥，早前对齐未提交改动堆积，建议主理人确认提交策略。

---

## 三、合并后服务清单与端口清单（最终快照）

**服务（7 个）**：`nginx-gateway` / `mysql` / `redis` / `prj-redis` / `prj-backend-c` / `prj-frontend` / `prj-php`

**宿主端口映射（loopback 127.0.0.1）**
| 端口 | 服务 | 说明 |
|---|---|---|
| 80 | nginx-gateway | HTTP（TLS 终止在 VPS frps） |
| 443 | nginx-gateway | HTTPS |
| 33060 | mysql | MySQL 宿主访问 |
| 63790 | redis | Redis 宿主访问 |
| 8081 | prj-frontend | 前端 loopback |
| 1181 | prj-php | 班级网站 loopback（供 frpc） |
| — | prj-redis | **无宿主端口**（仅 dev-network 内可达） |

---

## 四、最终结论

| 验证项 | 结论 |
|---|---|
| ① 合并配置合法 | ✅ PASS |
| ② class_init 挂载移除 / mysql·redis 完好 | ✅ PASS |
| ③ mysql_init 新 SQL 存在·幂等·有方案A注释 | ✅ PASS |
| ④ class_init 目录删除 / 无残留活动引用 | ✅ PASS |
| ⑤ CRLF / 幂等 | ✅ PASS |
| ⑥ 改动范围干净 / 密码未变 / 备份存在 | ✅ PASS（附 O4 观察） |
| ⑦ 无密码泄漏或篡改 | ✅ PASS |

**总体结论：通过（PASS）。**

**路由判定：NoOne。**
- 未发现任何【配置 bug】（config 退出码 0 无 error；class_init 挂载确已移除；mysql entrypoint/command 完好；新 SQL 幂等正确、全新卷不会中断；`.env.prod` 未被改）。
- 方案 A 实际改动范围隔离干净（仅声明的 4 项），`docker compose config` 全绿、端口/服务清单符合预期。
- 观察项 O1–O4 均为历史预存或非方案 A 引入，按约束记录、不误判、不擅自修改文档，无需路由工程师修复。

> 唯一需主理人拍板的是观察项 O4：工作区存在早前"对齐"未提交改动，建议确认是否随本次一并验收/提交。
