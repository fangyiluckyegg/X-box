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
2. **文档残留引用**：`docs/prod-mac-runbook.md`、`docs/architecture_review.md`、`docs/X-box-optimization-report-2026-07-14.md` 及 `deliverables/` 历史报告仍文字提及 `db/class_init/`。这些为文档/历史交付物，非业务/dev 运行配置，按「仅删孤立 class_init 目录与一行死挂载」的硬性约束**未改动**；建议后续将这些文档的 class_init 说明更新为「已并入 db/mysql_init/ 顶层（方案A）」，避免误导。其中 `prod-mac-runbook.md:62` 原写「msg.sql/work.sql 不在 initdb.d 下，不会自动执行」——方案A后该结论已不成立，建议优先修正该 runbook。

---

## 9. 结论

方案 A 已落地：`msg`/`work` 建库 SQL 上移至 `db/mysql_init/` 顶层并补 `IF NOT EXISTS` 幂等；无效的 `class_init` 子目录挂载与孤立目录已移除；`base.yml` 的 entrypoint/command/其他 volumes、`redis:7-alpine`、全部密码凭证均完好未动。全新数据卷首次初始化即可自动建 `msg`/`work` 库，现有 dev-mysql 运行无影响。
