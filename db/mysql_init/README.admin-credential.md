# 管理员弱口令治理说明（P1）

> 关联文件：`db/mysql_init/init.sql`（prj_dev 库 user_info 表默认管理员）、
> `Niu_Txl/ensure_admin_hash.php`（msg / work 库 admin_user）。
> 关联审计项：默认 `admin / admin123` 弱口令。

## 1. 当前现状（已查清）

| 管理员账号 | 所在库 / 表 | 口令来源 | 是否弱口令 | 状态 |
| --- | --- | --- | --- | --- |
| `admin` | `prj_dev.user_info` | `init.sql` 硬编码 BCrypt（= admin123） | **是** | ❌ 待治理 |
| `admin` | `msg.admin_user` | `ensure_admin_hash.php` 读 `ADMIN_INIT_PWD` | 否（prod 强随机） | ✅ 已 env 驱动 |
| `admin` | `work.admin_user` | `ensure_admin_hash.php` 读 `ADMIN_INIT_PWD` | 否（prod 强随机） | ✅ 已 env 驱动 |

- `prj_dev.user_info` 的 `admin / admin123` 是**唯一**残留的硬编码弱默认口令。
- `msg` / `work` 的 `admin_user` 早已通过 `ensure_admin_hash.php` 从环境变量 `ADMIN_INIT_PWD`
  注入 BCrypt 哈希（`.env.dev` = `Admin@2026`，`.env.prod` = `Xb0xAdm902_Pq7Kv3RtM`），
  **不在弱口令范围内**，无需改动。

## 2. 为何未直接做「env 注入」（方案 A 不可行）

方案 A 要求在 SQL 中写 `INSERT ... VALUES('${ADMIN_USER}','${ADMIN_PWD_HASH}')`。
但本项目的数据库初始化机制是：

- `db/mysql_init/*.sql` 由 MySQL 官方镜像在**首次初始化**时直接执行
  （挂载到 `/docker-entrypoint-initdb.d`），**没有任何 envsubst / 模板变量替换环节**
  （全仓检索 `envsubst` 为 0 处；仅有 `init.template.sql.template` 仅供人工参考，无自动处理）。
- 因此 `${ADMIN_INIT_PWD}` 这类占位符会被**原样当字符串写入**，导致账号名变成字面量
  `${ADMIN_USER}`，完全失效。
- 要让方案 A 成立，必须先引入「init 前对 SQL 做 envsubst」的步骤（改 compose / 启停脚本），
  属新增基础设施，超出最小变更范围。故按任务规则回退到方案 B。

## 3. 已采用的治理（方案 B：保留默认 + 文档化 + 标记后续）

- `init.sql` 中该 INSERT 已加 ⚠️ 注释，明确其为弱口令、并指向本文件；**保留默认账号**以保证首次启动可登录。
- 未硬造「首登强制改密」登录流程：后端（Spring Boot `prj-backend-c`）当前**无**该机制，
  任务明确要求不要伪造不存在的登录流程。
- `init.sql` 仅在**全新数据卷首次初始化**时执行一次；存量库不会重跑，故改密必须靠后续手段。

## 4. 推荐后续开发（请 team-lead 决策排期）

优先级从高到低：

1. **（推荐·彻底）env 注入 + 启动期生成哈希**
   在 MySQL 启动前对 `init.sql` 做 envsubst（或新增一个等价 PHP/CLI 引导脚本，仿
   `ensure_admin_hash.php`），用 `ADMIN_INIT_PWD` 在运行时生成 BCrypt 哈希写入 `user_info`。
   变量命名直接复用既有 `ADMIN_INIT_PWD`（用户名固定为 `admin`，无需新增 `ADMIN_USER`）。
   优点：与 msg/work 的 admin 治理方式完全一致，零硬编码口令。

2. **（产品向）首登强制改密**
   在后端 Spring Security 登录流程增加 `must_change_pwd` 标记；首次登录强制跳转改密页，
   改密前禁止访问 `/api`。需后端开发，属新功能，不属本次最小变更。

3. **（运维临时）启动后手动改密**
   首次部署后，运维执行一次性 `UPDATE user_info SET password=<强随机 BCrypt> WHERE user_name='admin';`
   或导出带强口令的 `init.sql` 变体用于全新建库。临时止血，不治本。

## 5. 环境变量命名约定（已对齐）

- 现有约定：`ADMIN_INIT_PWD`（见 `.env.dev`、`.env.prod`；`ensure_admin_hash.php` 读取）。
- 无需 `ADMIN_USER`：管理员用户名固定为 `admin`。若后续引入方案 A，请沿用 `ADMIN_INIT_PWD` 命名，
  不要新建 `ADMIN_PWD` / `ADMIN_USER` 等，避免与既有约定漂移。
