# X-box 根目录历史 bak 文件清理 · 交付报告

**日期**：2026-07-20
**主理人**：齐活林（Delivery Director）
**团队成员**：寇豆码（Engineer）、严过关（QA）
**工作流**：快速清理模式（BugFix 类清理路径）

## TL;DR
按用户确认，删除 X-box 项目根目录 10 个历史临时备份文件（前几轮对齐/清理生成的 `*.bak-2026-07-20` 与 `.env.prod.bak`），并同步更新 `项目开发说明` 标注；QA 回归验证 docker compose 配置零回归。

## 交付概览
- **状态**：✅ 完成
- **测试通过率**：100%（QA 全 PASS）
- **智能路由判定**：NoOne
- **已知问题数**：0
- **git 操作**：全程未执行任何 git 命令（被 git 跟踪的 bak 显示为未提交删除状态，待用户后续统一 commit）

## 删除清单（共 10 个）
| # | 文件 |
|---|---|
| 1 | `.env.prod.bak` |
| 2 | `docker-compose.base.yml.aligned.bak-2026-07-20` |
| 3 | `docker-compose.base.yml.safe.bak-2026-07-20` |
| 4 | `docker-compose.base.yml.a.bak-2026-07-20` |
| 5 | `docker-compose.base.yml.llama.bak-2026-07-20` |
| 6 | `docker-compose.prod.yml.aligned.bak-2026-07-20` |
| 7 | `docker-compose.business-prj.dev.yml.safe.bak-2026-07-20` |
| 8 | `docker-compose.business-prj.yml.safe.bak-2026-07-20` |
| 9 | `.env.dev.example.safe.bak-2026-07-20` |
| 10 | `.env.dev.safe.bak-2026-07-20` |

## 保留文件（主动确认未误删）
- `.env.dev.example`（被 `docs/prod-mac-runbook.md` 作为 `cp .env.dev.example .env.dev` 模板引用）
- `.env.prod.example`（被 `docs/prod-mac-runbook.md` 作为 `cp .env.prod.example .env.prod` 模板引用）
- `.env.backend`（被 `docker-compose.business-prj.dev.yml` 作为 `env_file` 注入后端容器）
- `.env.dev`、`.env.prod`（正在使用的环境文件）

## 文档更新（项目开发说明）
| 行 | 修改前 | 修改后 |
|---|---|---|
| .env.dev.example（L84） | `# 是否可删除？` | `# 保留：被 docs/prod-mac-runbook.md 作为 cp 模板引用` |
| .env.prod.bak（L85） | `# 是否可删除？` | 整行删除 |
| .env.prod.example（L86） | `# 是否可删除？` | `# 保留：被 docs/prod-mac-runbook.md 作为 cp 模板引用` |

## QA 回归验证（严过关）
- `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config` → **EXIT=0**，stderr 为空
- 服务数 = **7**：nginx-gateway / mysql / redis / prj-redis / prj-backend-c / prj-frontend / prj-php
- 宿主端口 80/443/33060/63790/8081/1181 各 **1 次**，无冲突
- prj-redis **无 host 端口映射**（符合设计）
- 10 个 bak 文件全部 NOT_EXIST；保留 5 文件全部 EXIST
- 全仓活动配置（include/env_file/volumes/compose）**无任何 bak 引用**（引用仅存于 `docs/` 历史报告）
- 开发说明 `.env.prod.bak` 字符串已清零、两处 example 标注含"保留"

## 文件清单
- 删除：10 个 bak 文件（见上表）
- 修改：`项目开发说明`
- 报告：`docs/deliverables/software-company/xbox-cleanup-bakfiles-2026-07-20.md`

## 用户下一步建议
1. **收敛工作区（仍待做）**：十轮+改动均未提交，择机 `git add -A && git commit` 入库即可。
2. **无需其他操作**：清理不影响 compose 运行与部署模板复制（example 文件完好保留）。
