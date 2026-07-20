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
  - `docs/architecture_review.md:181`（历史架构评审报告描述）
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
