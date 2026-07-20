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
- **全局 grep 结论**：`MYSQL_ROOT_PWD` 在整个仓库中**无任何代码消费者**——`db/mysql_scripts/docker-entrypoint-wrapper.sh` 读取的是 `MYSQL_ROOT_PASSWORD`；各 compose（含 `docker-compose.base.yml`）亦只引用 `MYSQL_ROOT_PASSWORD`；仅历史交付报告 `docs/deliverables/software-company/xbox-prod-align-2026-07-20.md` 的文字中提及该冗余键。故删除安全，不影响任何功能。
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
