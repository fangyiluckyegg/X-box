# X-box 容器化项目 — 交付报告（2026-07-16）

## TL;DR
完成 X-box 架构巡检（22 项风险）→ O1 安全去密（仓库工作树零明文）→ QA 静态校验 7/7 通过 → 文档明文脱敏；prod 栈可在 Mac 上参考已跑通的 dev 跑通。剩余历史明文清理与 Mac 实测由用户在 Mac 侧执行。

## 范围与工作流
- 工作流：部分工作流（架构评审）+ 配置修复（O1 实现）+ QA 校验 + 文档脱敏。
- 项目根：`D:/crh123dexiaohao/X-box/`
- 技术栈：Nginx 网关 + MySQL8 + Redis + Spring Boot3（Java17）后端 + Vue2 前端 + Ollama（bge-m3 向量服务）+ PHP7.4 班级网站（Niu_Txl）。
- 目标生产环境：Mac Mini M2 16G / macOS Sonoma 14.8.7 / OrbStack 2.1.3。
- 约束：dev 环境已全部跑通；prod 还需参考 dev 测试跑通。

## 阶段产出

### 1. 架构巡检（架构师 · 部分工作流）
- 交付 `docs/architecture_review.md`：22 项风险（P0×3 / P1×8 / P2×11）。
- 根因聚焦：
  - **R-01** 生产库口令/库名不一致（`.env.prod` 强随机 vs `init.sql` 硬编码 `Prj@Dev789`；`MYSQL_DATABASE=prj_dev` 与注释 `prj_prod` 矛盾）。
  - **R-02** prod 编排伪自包含（头注释称不依赖 base.yml，实则依赖 `dev-mysql`/`dev-network`/`dev-prj-llama`）。
  - **R-03** `init.sql` 明文口令入仓（`.gitignore` 未忽略）。
- 拓扑：`docs/architecture_current_topology.mmd`、`docs/architecture_target_topology.mmd`。

### 2. O1 安全去密（工程师 · 凭证策略 O1）
- 凭证策略：**O1 安全去密**——`init.sql` 只留库/表/种子，`prj_user` 由 `db/mysql_scripts/docker-entrypoint-wrapper.sh` 的 `ensure_app_user()` 按 gitignored 的 `.env` 运行时注入，仓库零明文。
- 改动：
  - `db/mysql_init/init.sql`：删除 `prj_user` 明文 `CREATE USER ... IDENTIFIED BY 'Prj@Dev789'` + 注释态死代码 `bibutong_user`（含明文 `BuButong@Dev789`）；仅保留 `prj_dev`+`USE`+`user_info`/`employee_kpi` DDL+`admin` BCrypt 哈希（49 行，UTF-8/LF）。
  - `.env.prod`：GBK→UTF-8（无 BOM）/LF；`PRJ_DB_PWD`==`SPRING_DATASOURCE_PASSWORD` 设强随机值；新增【凭证契约·必须一致】块。
  - `docker-compose.prod.yml`：头注释扶正为诚实「必须搭配 base.yml 启动」+【凭证契约】块；base.yml 的 `mysql`/`dev-mysql`/`dev-network` 三者对齐确认。
  - 新增 `docs/prod-mac-runbook.md`（8 节：前置/凭证契约/启动命令/验证/已知手动步骤/Ollama-CPU 提醒/GBK 转码/禁止改动清单）。
- 交叉验证：读 `backend/prj-backend-c/src/main/java/com/prj/framework/config/StartupSecurityValidator.java` 确认 prod 严格校验只拒弱值集（`Prj@Dev789`/`redis_default_pass_change_me`/`Druid@Dev2024`/默认占位）及 null/blank，强制 `JWT_SECRET`≥32 字节；**不**校验 DB/Redis/Druid 口令复杂度 → 该口令（非弱值）与 64 字节 `JWT_SECRET` 可过。

### 3. QA 静态校验（QA 工程师）
- 7/7 PASS：① init.sql 无明文；② `.env.prod` 编码+9 变量+两口令相等；③ compose 变量引用一致；④ base.yml 服务名对齐；⑤ gitignore `.env*` 忽略 `.env.prod` 未入仓；⑥ StartupSecurityValidator 交叉验证；⑦ 未动 dev/无回归。
- 智能路由判定：**NoOne（成功）**——O1 范围内无源码/配置 Bug。

### 4. 文档明文脱敏（工程师 · remediation ①）
- 4 份 git-tracked 文档 + 临时脚本 `scan_secrets_tmp.py` 的 7 个「当前生产」密钥 → `<REDACTED-live-prod>`。
- 整仓 Grep 特征串（除 gitignored `.env.prod`）全部 `No matches`。
- 历史/弱默认值（`Prj@Dev789` 等）按团队决策**保留**（硬编码在源码，仅抹文档会破坏校验器与测试，且无安全增益）。

## 文件清单

### 修改
- `D:/crh123dexiaohao/X-box/db/mysql_init/init.sql`
- `D:/crh123dexiaohao/X-box/.env.prod`
- `D:/crh123dexiaohao/X-box/docker-compose.prod.yml`
- `D:/crh123dexiaohao/X-box/docs/X-box-p0-remediation-log-2026-07-14.md`
- `D:/crh123dexiaohao/X-box/docs/X-box-p0-qa-verification-2026-07-14.md`
- `D:/crh123dexiaohao/X-box/docs/X-box-db-merge-log-2026-07-14.md`
- `D:/crh123dexiaohao/X-box/docs/architecture_review.md`

### 新建
- `D:/crh123dexiaohao/X-box/docs/prod-mac-runbook.md`
- `D:/crh123dexiaohao/X-box/docs/prod-secret-history-cleanup.md`
- `D:/crh123dexiaohao/X-box/scan_secrets_tmp.py`（已脱敏，**提交前必须删除**）

## 验证状态
| 项 | 状态 |
|----|------|
| O1 静态校验 | 7/7 PASS |
| 工作树明文 | 已清零（Grep 验证） |
| 历史明文 | 待 Mac 侧 `git filter-repo` |
| 实测启动 | 待 Mac 侧 |

## 已知问题 / WARN
- P0 遗留文档曾含生产明文（工作树已脱敏）；**历史提交**仍需 `git filter-repo` 清理。
- `scan_secrets_tmp.py` 提交前必须删除、确认未 `git add`。
- 沙箱无 docker daemon，未实启容器；最终需用户在 Mac 实测。

## 用户下一步（Mac 侧手动）
1. 对齐本机 `.env.dev` 与 `.env.prod` 的 `SPRING_DATASOURCE_PASSWORD`（O1 凭证契约，否则连不上共享 `dev-mysql`）。
2. 跑 `git filter-repo` 清历史明文（见 `docs/prod-secret-history-cleanup.md`）。
3. 按 `docs/X-box-db-merge-runbook-2026-07-14.md` 手动建 `class_user` 及 `msg`/`work` 库。
4. 先 `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config` 渲染校验，再 `up -d --build`。
5. `.env.prod` 上线前轮换口令。

## 团队
- `software-xbox-audit`（架构师 / 工程师 / QA）已完成全部阶段并正常解散。
