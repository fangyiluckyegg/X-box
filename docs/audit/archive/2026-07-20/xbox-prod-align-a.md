# X-box 容器化 · 方案 A 真修复报告（msg/work 全新卷自动建库）

- 日期：2026-07-20
- 执行角色：software-engineer（寇豆码）
- 范围：`D:\crh123dexiaohao\X-box\`
- 前序状态：dev→prod 对齐 + 安全整合（含上一轮「对齐」挂载 class_init 只读子目录）均已通过 QA。

---

## 1. 一句话摘要

方案 A 将 `db/class_init/msg.sql`、`work.sql` 两份建库脚本**上移到 `db/mysql_init/` 顶层**（initdb.d），并**删除无效的 class_init 只读子目录挂载**与孤立的 `db/class_init/` 目录，使 MySQL 在**全新数据卷首次初始化时自动建 `msg`/`work` 库**（官方 entrypoint 对 initdb.d 非递归，子目录永不执行，故必须置于顶层）。

---

## 2. 按模块改动表

| 模块 | 操作 | 文件:行号 | 改动要点 | 优先级 | 风险说明 |
|------|------|-----------|----------|--------|----------|
| msg 库自动初始化 | 新增文件（上移） | `db/mysql_init/msg.sql`（全文；关键：1–12 行【方案A】注释头；`admin_user`/`post`/`reply` 的 `CREATE TABLE` 均补 `IF NOT EXISTS`） | 原 `db/class_init/msg.sql` 全文搬入 initdb.d 顶层；头部改写方案A注释；`CREATE TABLE` 加 `IF NOT EXISTS` 幂等；建 `msg` 库/表/种子数据语义与原文一致 | 高(P0) | 低：仅全新卷首次初始化自动执行；已存在 `mysql_data` 卷不重跑；`class_user` 对 `msg.*` 授权由 wrapper 在 MySQL 就绪后完成（晚于 initdb.d），无冲突 |
| work 库自动初始化 | 新增文件（上移） | `db/mysql_init/work.sql`（全文；关键：1–12 行【方案A】注释头；`admin_user`/`work_pic`/`work_type` 的 `CREATE TABLE` 均补 `IF NOT EXISTS`） | 同上，建 `work` 库 | 高(P0) | 低：同上 |
| class_init 死挂载移除 | 删除挂载行 + 改写注释 | `docker-compose.base.yml` ：36–42（注释块改写为方案A说明）；原 `:49` 挂载行已删除（现为 `:49–50` 方案A注释） | 删除 `- ./db/class_init:/docker-entrypoint-initdb.d/class_init:ro`；上方说明块改为方案A注释；保留 `./db/mysql_init:/docker-entrypoint-initdb.d` 挂载 | 高(P0) | 极低：删除的是官方 entrypoint 永不执行的死挂载；未动 entrypoint/command、未动其他 volumes |
| class_init 目录清理 | 删除目录 | `db/class_init/`（含 `msg.sql`、`work.sql`、`msg.sql.safe.bak-2026-07-20`、`work.sql.safe.bak-2026-07-20`） | 内容已并入 `mysql_init`，删除孤立目录以消除与顶层重复造成的混淆 | 中 | 低：唯一功能性引用即 base.yml 挂载（已删）；docs/历史交付报告仍文字提及 class_init 属文档残留，本次未改动（见 §6 意外发现） |
| 凭证契约 | 无改动 | `.env.dev` / `.env.prod` | 未触碰任何密码/密钥真实值 | — | 无 |

> 说明：原 `msg.sql`/`work.sql` 的 `CREATE DATABASE` 已带 `IF NOT EXISTS`，但 `CREATE TABLE` 原本为裸建（非幂等）；本次按方案A要求补 `IF NOT EXISTS`。`ALTER TABLE ... ADD PRIMARY KEY` 与 `INSERT` 保留原 phpMyAdmin dump 语义——在全新卷首次执行路径下完全正确；仅在「手动重复执行本文件」场景下 `ADD PRIMARY KEY` 会报 1050（主键已存在），属预存边界情况，不影响全新卷自动初始化（详见 §6）。

---

## 3. 新并入 mysql_init 的文件清单

| 绝对路径 | 文件作用 |
|----------|----------|
| `D:\crh123dexiaohao\X-box\db\mysql_init\msg.sql` | 全新数据卷首次初始化时自动执行：建 `msg` 库（utf8）、建 `admin_user`/`post`/`reply` 表并写入种子留言数据；`class_user` 授权由 wrapper 后续完成，本文件仅建库建表。 |
| `D:\crh123dexiaohao\X-box\db\mysql_init\work.sql` | 同上：建 `work` 库、建 `admin_user`/`work_pic`/`work_type` 表并写入种子作品数据。 |

执行顺序（initdb.d 字母序）：`init.sql` → `init.template.sql` → `migrate_role.sql` → **`msg.sql`** → **`work.sql`**。`msg`/`work` 自包含、无跨脚本依赖，置于末尾安全。

---

## 4. class_init 移除说明 + 对运行时的影响

- **移除内容**：
  1. `docker-compose.base.yml` 中 `- ./db/class_init:/docker-entrypoint-initdb.d/class_init:ro` 死挂载行（官方 entrypoint 非递归，子目录 `.sql` 被 `ignoring` 分支跳过，永不执行，QA 已确认）。
  2. 孤立目录 `db/class_init/`（含 `msg.sql`、`work.sql` 及上一轮 `.safe.bak` 备份）——内容已并入 `db/mysql_init/`，删除以消除重复与误导。
- **对运行时的影响（关键）**：
  - **仅影响「全新数据卷首次初始化」**：MySQL 官方 entrypoint 只在 `mysql_data` 命名卷为空时才跑 initdb.d；`msg.sql`/`work.sql` 上移顶层后，全新卷会自动建 `msg`/`work` 库。
  - **现有 dev-mysql 数据卷不受影响**：已存在 `mysql_data` 卷不会重跑 initdb.d，运行行为完全不变（dev 既有 `msg`/`work` 库与数据保持原样）。
  - **class_user 授权链路不变**：仍由 `db/mysql_scripts/docker-entrypoint-wrapper.sh` 的 `ensure_class_user()` 在 MySQL 就绪后幂等授权 `msg.*`/`work.*`，与本次上移无冲突。

---

## 5. 已修改/删除文件清单（绝对路径 + 备份路径）

| 操作 | 文件 | 备份路径 |
|------|------|----------|
| 修改 | `D:\crh123dexiaohao\X-box\docker-compose.base.yml` | `D:\crh123dexiaohao\X-box\docker-compose.base.yml.a.bak-2026-07-20` |
| 新增 | `D:\crh123dexiaohao\X-box\db\mysql_init\msg.sql` | （新建，无备份） |
| 新增 | `D:\crh123dexiaohao\X-box\db\mysql_init\work.sql` | （新建，无备份） |
| 删除 | `D:\crh123dexiaohao\X-box\db\class_init/`（整体） | （按方案A不另备份；内容已并入 `mysql_init`） |

> 备份命名遵循统一约定 `.a.bak-2026-07-20`（区别于上一轮 `.safe.bak`）。

---

## 6. 凭证契约无需变更（重点）

**本次改动不涉及任何密码/密钥/令牌真实值，凭证契约完全不变：**
- `.env.dev`、`.env.prod` 未被读取或修改，真实密码零改动。
- `msg.sql`/`work.sql` 仅建库/建表/写种子数据，不含任何账号口令（原 `admin_user.password` 为空串，与历史 dump 一致，未改动）。
- `class_user` 口令仍由 `.env.dev` 的 `CLASS_DB_PWD` 经 wrapper `ensure_class_user()` 注入，链路与上轮一致。
- `redis` 口令（`REDIS_PASSWORD`）、`MYSQL_ROOT_PASSWORD` 等均未被触碰。

---

## 7. Mac 全新部署自动建库验证命令

> 前提：Mac（M 系列，Docker Desktop / OrbStack）首次部署，`mysql_data` 命名卷不存在或为空，才会触发首次初始化。

```bash
# 1) 全新拉起 mysql 服务（若曾跑过，先 docker compose down -v 丢弃 mysql_data 以强制首次初始化）
docker compose -f docker-compose.base.yml up -d mysql

# 2) 观察初始化日志，应出现 Running .../msg.sql 与 .../work.sql，且无致命报错
docker compose -f docker-compose.base.yml logs --follow mysql 2>&1 \
  | grep -iE "Running .*(msg|work)\.sql"

# 3) 进入容器核对库已自动创建（应含 msg、work）
docker compose -f docker-compose.base.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"

# 4) 进一步核对表与种子数据
docker compose -f docker-compose.base.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE msg; SHOW TABLES; SELECT COUNT(*) AS post_cnt FROM post; \
   USE work; SHOW TABLES; SELECT COUNT(*) AS work_pic_cnt FROM work_pic;"
# 期望：msg 库 post 表 5 行、work 库 work_pic 表 12 行（与原始 dump 一致）
```

> 若 `SHOW DATABASES` 未出现 `msg`/`work`：说明 `mysql_data` 卷为存量（未重跑 initdb.d），属预期——此时按历史 runbook 手动补救即可；全新卷必自动建库。

---

## 8. 意外发现 / 备注（超出方案A范围，未改动）

1. **`init.template.sql` 预存瑕疵（不影响方案A）**：`db/mysql_init/init.template.sql` 含裸 `CREATE TABLE user_info`，而 `init.sql` 已先建同名表。在全新卷上该文件会报 `ERROR 1050 (42S01) Table 'user_info' already exists`。但官方 MySQL 8.0 entrypoint 对 initdb.d 脚本失败仅记录日志、**不中断 `for` 循环**（脚本无 `set -e` 包裹该循环），故其后的 `msg.sql`/`work.sql` 仍会执行，**方案A在全新卷自动建 msg/work 库不受影响**。该重复建表属历史遗留，建议后续将 `init.template.sql` 移出 initdb.d 或加 `IF NOT EXISTS`，但不在本次方案A范围内。
2. **文档残留引用**：`docs/deployment/prod-mac-runbook.md`、`docs/architecture/architecture_review.md`、`docs/audit/archive/2026-07-14/docs/X-box-optimization-report-2026-07-14.md` 及 `docs/deliverables/` 历史报告仍文字提及 `db/class_init/`。这些为文档/历史交付物，非业务/dev 运行配置，按「仅删孤立 class_init 目录与一行死挂载」的硬性约束**未改动**；建议后续将这些文档的 class_init 说明更新为「已并入 db/mysql_init/ 顶层（方案A）」，避免误导。其中 `prod-mac-runbook.md:62` 原写「msg.sql/work.sql 不在 initdb.d 下，不会自动执行」——方案A后该结论已不成立，建议优先修正该 runbook。

---

## 9. 结论

方案 A 已落地：`msg`/`work` 建库 SQL 上移至 `db/mysql_init/` 顶层并补 `IF NOT EXISTS` 幂等；无效的 `class_init` 子目录挂载与孤立目录已移除；`base.yml` 的 entrypoint/command/其他 volumes、`redis:7-alpine`、全部密码凭证均完好未动。全新数据卷首次初始化即可自动建 `msg`/`work` 库，现有 dev-mysql 运行无影响。

---


## QA 复验

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
| `docs/deliverables/software-company/*.md`（5 份历史交付报告） | 历史报告文字 | 否（按约束排除） |
| `项目开发说明`、`docs/architecture/architecture_review.md`、`docs/audit/archive/2026-07-14/docs/X-box-optimization-report-2026-07-14.md`、`docs/deployment/prod-mac-runbook.md` | 文档/运行手册文字 | 否（文档文字；`prod-mac-runbook.md` 见观察项 O3） |

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
- **O3（预存文档）**：`docs/deployment/prod-mac-runbook.md` 仍文字引用 `class_init`（历史运行手册）。按约束**不擅自改文档**，仅作观察记录。
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
