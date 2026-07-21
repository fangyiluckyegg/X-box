# X-box 日志目录清理补充报告（2026-07-20）

## 摘要

在用户明确确认下，本次对 X-box 项目执行了一次低风险废弃日志目录清理：删除容器化 Ollama 时代的空目录 `logs/llama`，以及前端源码树下无引用的空目录 `web/prj-frontend/logs/prj-frontend`（并顺带清空删除其变空的父目录 `web/prj-frontend/logs`）；运行所需目录 `logs/prj-frontend`、`logs/prj-php` 及其余 `logs/*` 子目录均完好保留，未改动任何 compose / Dockerfile / volumes 配置与凭证。

## 操作清单

| 模块 | 操作 | 路径 | 改动要点 | 优先级 | 风险说明 |
| --- | --- | --- | --- | --- | --- |
| logs/llama | 删除整个目录 | `D:\crh123dexiaohao\X-box\logs\llama\` | 容器化 Ollama 时代的日志目录，已无任何 compose/Dockerfile/脚本引用；删除前经 `ls` 复核为**空目录**（无文件），无用户私有数据或密钥/token 等敏感文件，可安全丢弃 | 低 | 极低。删除前已 Grep 全仓确认仅备份 compose（注释行）与历史报告引用，无运行引用；删除后再次 Grep 确认无新增运行引用 |
| web/prj-frontend/logs/prj-frontend | 删除空子目录 | `D:\crh123dexiaohao\X-box\web\prj-frontend\logs\prj-frontend\` | 前端源码树下的空目录，删除前 `ls` 复核为**空目录**；全仓无 compose/Dockerfile/脚本引用 | 低 | 极低。删除后可避免其被前端 `Dockerfile.prod` 的 `COPY . .` 阶段带入镜像构建上下文，使构建上下文更干净（不影响运行） |
| web/prj-frontend/logs（父目录） | 删除变空的父目录 | `D:\crh123dexiaohao\X-box\web\prj-frontend\logs\` | 删除 `prj-frontend` 子目录后，父目录 `logs` 已为空，按既定步骤顺手一并删除，保持前端源码树整洁 | 低 | 极低。同上为冗余空目录，无运行引用 |
| logs/prj-frontend | 保留（未动） | `D:\crh123dexiaohao\X-box\logs\prj-frontend\` | 被 `docker-compose.business-prj.dev.yml:23`、`.business-prj.yml:26`、`.prod.yml:122` 挂载（nginx/dev 日志），属运行必需 | 高（必须保留） | 严禁删除。本次未触碰 |
| logs/prj-php | 保留（未动） | `D:\crh123dexiaohao\X-box\logs\prj-php\` | 被 `docker-compose.prod.yml:151`、`.classphp.dev.yml:20` 挂载（apache2 日志），属运行必需 | 高（必须保留） | 严禁删除。本次未触碰 |

## 删除前目录内容复核结果

- **`logs/llama/`**：经 `ls -la` 复核，仅含 `.` 与 `..`，为**空目录**，无任何日志文件、密钥、token 或用户私有数据。直接整体删除，符合预期（容器化 Ollama 已迁宿主机，该目录早已废弃）。
- **`web/prj-frontend/logs/`**：删除前仅含一个子目录 `prj-frontend`。
- **`web/prj-frontend/logs/prj-frontend/`**：经 `ls -la` 复核，`total 0`，为**空目录**，无任何文件。
- 两处目标均为空目录，删除前已确认无敏感文件，误删风险为零。

## 保留项说明

- **`logs/prj-frontend` 必须保留**：被以下运行配置挂载（nginx/dev 日志）：
  - `docker-compose.business-prj.dev.yml:23`
  - `.business-prj.yml:26`
  - `.prod.yml:122`
- **`logs/prj-php` 必须保留**：被以下运行配置挂载（apache2 日志）：
  - `docker-compose.prod.yml:151`
  - `.classphp.dev.yml:20`
- 上述两目录本次**未做任何改动**，删除操作后再次 `ls` 复核确认其依旧存在且内容完好（`logs/prj-php` 仍含实时 `access.log`、`error.log`）。
- 其余运行所需子目录 `logs/mysql`、`logs/nginx`、`logs/redis`、`logs/prj-backend-c` 同样未动，均保留在原位。

## 全仓残留引用排查结论

- **`logs/llama` 全仓引用**：删除前与删除后两次 Grep 结果一致，仅命中以下非运行引用，均为**注释行或历史文档**，不构成运行依赖：
  - `docker-compose.base.yml.a.bak-2026-07-20:133`（注释 `#     - ./logs/llama:/app/logs/llama`）
  - `docker-compose.base.yml.llama.bak-2026-07-20:134`（注释）
  - `docker-compose.base.yml.aligned.bak-2026-07-20:122`（注释）
  - `docker-compose.base.yml.safe.bak-2026-07-20:125`（注释）
  - `docs/architecture/architecture_review.md:181`（历史架构评审报告描述）
  - 无任何处于生效状态的 compose / Dockerfile / 脚本引用 `logs/llama`。
- **`web/prj-frontend/logs` 全仓引用**：删除前与删除后两次 Grep 均**无匹配**（`No matches found`），确认全仓无任何运行配置引用该路径（含其 `prj-frontend` 子目录）。历史报告中亦未发现有效运行引用。

## 影响与契约确认

- **凭证契约无需变更**：本次仅删除废弃/冗余空日志目录，未读取、未修改任何密码、密钥、令牌真实值；所有 `.env`、凭证文件、密钥挂载均原样保留。
- **dev/prod 运行行为不变**：未改动任何 `compose` / `Dockerfile` / `volumes` 配置；被挂载的运行目录 `logs/prj-frontend`、`logs/prj-php` 及 `logs/mysql`、`logs/nginx`、`logs/redis`、`logs/prj-backend-c` 均完好保留，容器日志挂载行为与原有一致。
- **合并 `docker compose config` 不受影响**：删除的 `logs/llama` 仅在已注释的备份 compose 中出现，未出现在任何生效的 compose 文件中；`web/prj-frontend/logs` 全仓无引用。执行 `docker compose config` / `docker compose -f ... config` 合并结果与清理前完全一致，无挂载缺失或校验报错。

## 改动文件清单

本次为纯删除废弃目录操作，**除本补充报告外，无任何新增或修改的代码/配置文件**：

- 删除：`D:\crh123dexiaohao\X-box\logs\llama\`（空目录）
- 删除：`D:\crh123dexiaohao\X-box\web\prj-frontend\logs\prj-frontend\`（空目录）
- 删除：`D:\crh123dexiaohao\X-box\web\prj-frontend\logs\`（变空后一并删除的父目录）
- 新增报告：`D:\crh123dexiaohao\X-box\docs\deliverables\software-company\xbox-cleanup-logs-2026-07-20.md`

---


## QA 复验

# X-box 日志目录清理 — QA 复验报告

- **日期**：2026-07-20
- **验证人**：QA 工程师（严过关）
- **验证性质**：独立只读复验，未修改任何业务文件
- **范围**：复验「日志目录清理」改动（删除 `logs/llama`、删除 `web/prj-frontend/logs/prj-frontend` 及其变空的父目录 `web/prj-frontend/logs`）
- **环境**：Windows + Git Bash；Docker 29.6.1；项目根目录 `D:\crh123dexiaohao\X-box\`

## 结论：通过（PASS），路由判定：NoOne

6 项验证全部 PASS（含 1 项澄清项 PASS）。未发现任何配置 bug（无目录误删、全仓无运行引用残留、合并 `docker compose config` 合法、无密码/密钥篡改）。本轮清理改动范围与任务说明完全一致，`Dockerfile.prod` 的 `M` 已确认为首轮历史残留，非本轮引入。无需转交 Engineer，校验脚本/命令本身无误，故路由 **NoOne**。

---

## 逐项验证

### 1. 废弃目录已删除 — PASS

| 检查对象 | 命令 | 证据 | 结果 |
|---|---|---|---|
| `logs/llama` | `ls -la "D:/crh123dexiaohao/X-box/logs/llama"` | `ls: cannot access '.../logs/llama': No such file or directory`（exit 2） | 不存在 ✓ |
| `web/prj-frontend/logs` | `ls -la "D:/crh123dexiaohao/X-box/web/prj-frontend/logs"` | `ls: cannot access '.../web/prj-frontend/logs': No such file or directory`（exit 2） | 不存在 ✓ |
| 交叉验证 `**/logs/llama/**`（Glob） | — | `No files found` | 不存在 ✓ |
| 交叉验证 `**/web/prj-frontend/logs/**`（Glob） | — | `No files found` | 不存在 ✓ |

推论：`web/prj-frontend/logs/prj-frontend` 的父目录 `web/prj-frontend/logs` 已删除，子目录自然不存在。
→ **PASS**

### 2. 保留目录完好 — PASS

命令：`ls -d "/d/crh123dexiaohao/X-box/logs/prj-frontend" "/d/crh123dexiaohao/X-box/logs/prj-php" "/d/crh123dexiaohao/X-box/logs/mysql" "/d/crh123dexiaohao/X-box/logs/nginx" "/d/crh123dexiaohao/X-box/logs/redis" "/d/crh123dexiaohao/X-box/logs/prj-backend-c"`

输出（exit 0，6 个目录全部列出）：
```
/d/crh123dexiaohao/X-box/logs/mysql
/d/crh123dexiaohao/X-box/logs/nginx
/d/crh123dexiaohao/X-box/logs/prj-backend-c
/d/crh123dexiaohao/X-box/logs/prj-frontend
/d/crh123dexiaohao/X-box/logs/prj-php
/d/crh123dexiaohao/X-box/logs/redis
```

`logs/prj-php` 实时日志确认：
命令：`ls -la "/d/crh123dexiaohao/X-box/logs/prj-php"`
输出：
```
-rw-r--r--  access.log              808611  Jul 20 17:04
-rw-r--r--  error.log                12851  Jul 20 16:03
-rw-r--r--  other_vhosts_access.log      0  Jul 14 23:28
```
含实时写入的 `access.log`/`error.log`，未被误删。
→ **PASS**

### 3. 全仓无运行引用 — PASS

**Grep `logs/llama`（全仓）命中清单**——均为备份 compose 注释行 / 历史报告 / 本轮清理文档，无任何生效 compose / Dockerfile / 脚本运行引用：

- `docker-compose.base.yml.aligned.bak-2026-07-20:122` — `#     - ./logs/llama:/app/logs/llama`（注释行）
- `docker-compose.base.yml.llama.bak-2026-07-20:134` — `#     - ./logs/llama:/app/logs/llama`（注释行）
- `docker-compose.base.yml.a.bak-2026-07-20:133` — `#     - ./logs/llama:/app/logs/llama`（注释行）
- `docker-compose.base.yml.safe.bak-2026-07-20:125` — `#     - ./logs/llama:/app/logs/llama`（注释行）
- `docs/architecture/architecture_review.md:181` — 历史架构评审报告
- `docs/audit/archive/2026-07-20/xbox-cleanup-logs.md` — 本轮清理报告（文档说明性文字，非运行引用）

说明：4 个 `.bak-*` 文件均为首轮/历史备份 compose，且引用行以 `#` 开头为注释；**无任何处于生效状态的 compose 文件引用 `logs/llama`**。

**Grep `web/prj-frontend/logs`（全仓）**：仅命中本轮清理报告文档（说明性文字），全仓无任何 compose / Dockerfile / 脚本运行引用。
→ **PASS**

### 4. 合并配置仍合法 — PASS

命令：`docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config`

- **退出码**：`CONFIG_EXIT=0`
- **stderr**：仅 5 条 `level=warning msg="The \"aJs\" variable is not set. Defaulting to a blank string."` 警告，**无 error**；该告警与 logs 清理无关（清理未改动任何 compose），属预存环境变量告警。
- **服务清单（config --services）**：`mysql / prj-redis / prj-backend-c / nginx-gateway / prj-frontend / prj-php / redis` —— 共 **7 个**，与预期集合 `{nginx-gateway, mysql, redis, prj-redis, prj-backend-c, prj-frontend, prj-php}` 完全一致。
- **宿主发布端口（published）去重计数，每个恰一次**：
  - `80 × 1`、`443 × 1`、`33060 × 1`、`63790 × 1`、`8081 × 1`、`1181 × 1` —— 与预期端口逐一对应、无重复。
- **prj-redis 端口映射检查**：prj-redis 服务块内无任何 `published:` / `ports:` 行 → **无宿主端口映射**（符合预期）。
→ **PASS**

### 5. 无密码/密钥篡改 — PASS

- `.env.prod` / `.env.dev` 均被 git 忽略：`git check-ignore .env.prod .env.dev` 返回二者路径（exit 0），且 `git ls-files --error-unmatch .env.prod` 报错 `did not match any file(s) known to git` → 二者不被 git 跟踪，不出现在 `git status`，无修改。
- `git diff -- .env.prod` 与 `git diff -- .env.dev` 均为空（diff-exit=0）→ 真实密码/密钥值未变更。
- 本轮新增报告 `docs/audit/archive/2026-07-20/xbox-cleanup-logs.md` 扫描 secret 关键词（password / secret / token / 密钥 / 口令 / MYSQL_ROOT_PASSWORD / api_key 等）：仅命中文档性陈述（"无密钥/token 等敏感文件"、"未读取、未修改任何密码、密钥、令牌真实值"），**无任何明文密码/密钥真实值**。
- 清理对象为两个空目录，删除前已确认不含任何敏感文件。
→ **PASS**

### 6. 改动范围澄清 — PASS（澄清项，非缺陷）

- `git -C D:/crh123dexiaohao/X-box status --porcelain` 中确实出现 ` M web/prj-frontend/Dockerfile.prod`（第二列 M = 工作区未暂存修改）。
- `git diff -- web/prj-frontend/Dockerfile.prod` 确认该修改**仅为** `EXPOSE 80 → EXPOSE 8081`（首轮 dev→prod 对齐），与 logs 清理无关：
  ```
  -EXPOSE 80
  +EXPOSE 8081  # 实际监听端口见 nginx.conf（listen 8081）；与网关 prj.conf 代理目标(prj-frontend:8081)一致
  ```
- 佐证：工作区存在首轮备份 `?? web/prj-frontend/Dockerfile.prod.aligned.bak-2026-07-20`，与任务说明"首轮已生成 `.aligned.bak-2026-07-20` 备份"一致。
- 本轮 logs 清理的 git 影响：删除的是未纳入版本控制的空目录（git 不跟踪空目录，故 porcelain 中无对应 `D` 记录），以及新增报告文件 `?? docs/audit/archive/2026-07-20/xbox-cleanup-logs.md`。日志清理本身未修改任何已跟踪文件。
→ 澄清成立，非本轮引入的缺陷。

---

## 保留项确认汇总

| 保留目录 | 状态 |
|---|---|
| `logs/prj-frontend`（被 dev/prod/business compose 挂载） | 存在 ✓ |
| `logs/prj-php`（含实时 access.log / error.log，被 prod/classphp compose 挂载） | 存在 ✓ |
| `logs/mysql` | 存在 ✓ |
| `logs/nginx` | 存在 ✓ |
| `logs/redis` | 存在 ✓ |
| `logs/prj-backend-c` | 存在 ✓ |

## 最终结论

- **验证结果**：6/6 项 PASS（含 1 项澄清 PASS）。
- **通过与否**：**通过（PASS）**。
- **路由判定**：**NoOne** —— 未发现任何配置 bug，无需转交 Engineer；校验脚本/命令本身无误，无需自检。清理改动范围与任务说明完全一致，`Dockerfile.prod` 的 `M` 已确认为首轮历史残留。
