# X-box 废弃脚本清理补充报告（docker-entrypoint-llama.sh）

- **日期**：2026-07-20
- **执行人**：软件工程师（寇豆码）
- **关联清理轮次**：dev→prod 对齐、安全整合、方案 A、ai_llama 清理、deliverables 迁移（均 QA 通过）之后的一次低风险废弃脚本清理。

## 一句话摘要

删除了已无调用方的容器化 Ollama 启动包装脚本 `docker/ollama/docker-entrypoint-llama.sh`（及其空目录 `docker/`），并完整备份归档，同步更新了《项目开发说明》的目录树与清理记录。

## 操作清单

| 模块 | 操作 | 文件 | 改动要点 | 优先级 | 风险说明 |
| --- | --- | --- | --- | --- | --- |
| Ollama 脚本 | 备份归档 | `docs/deliverables/software-company/xbox-cleanup-ollama-script-2026-07-20.bak.sh` | 原脚本 178 行、7696 字节逐字拷贝，非直接丢弃，便于后续查阅 | 高 | 极低：仅新增备份，不改任何运行配置 |
| Ollama 脚本 | 删除文件 | `docker/ollama/docker-entrypoint-llama.sh` | 删除前确认主 `docker-compose.base.yml` 已无 `dev-prj-llama` 服务、零可运行引用 | 高 | 极低：脚本无任何调用方 |
| Ollama 目录 | 删除空目录 | `docker/ollama/`（含 `docker/`） | 删除脚本后 `ollama/` 为空，进而 `docker/` 也为空（无 Dockerfile/.dockerignore 等其它文件），一并移除 | 中 | 极低：仅删空目录 |
| 项目开发说明 | 编辑 | `项目开发说明` | 删除目录树中 `├── docker/ollama` 及 `│   └── docker-entrypoint-llama.sh #待核验` 两行；文末追加「# 目录清理记录（2026-07-20 补充）」段落 | 中 | 极低：仅文档描述，不影响运行 |
| 补充报告 | 新增 | `docs/deliverables/software-company/xbox-cleanup-ollama-script-2026-07-20.md` | 本文件，记录清理范围、依据与复核结论 | 中 | 无 |

## 脚本备份归档位置

```
D:\crh123dexiaohao\X-box\docs\deliverables\software-company\xbox-cleanup-ollama-script-2026-07-20.bak.sh
```

备份内容与原脚本逐字节一致（178 行，7696 字节），保留原 POSIX `/bin/sh` 实现全貌（后台启动 `ollama serve`、轮询就绪、幂等拉取 bge-m3 模型、信号转发、降级标记等逻辑），未做任何裁剪。

## 删除说明

- **已删除文件**：`D:\crh123dexiaohao\X-box\docker\ollama\docker-entrypoint-llama.sh`
- **已删除空目录**：`D:\crh123dexiaohao\X-box\docker\ollama\` 与 `D:\crh123dexiaohao\X-box\docker\`
- **删除前目录状态**：`docker/` 下仅含 `ollama/` 一个子目录，且 `ollama/` 内仅此脚本；无任何 Dockerfile、`.dockerignore` 或其它文件，故删除脚本后两级目录均为空、一并移除，无其它资产损失。

## 项目开发说明更新要点

1. **目录树**：移除了原第 82–83 行附近的
   ```
   ├── docker/ollama
   │   └── docker-entrypoint-llama.sh  #待核验
   ```
   使目录树结构保持简洁、与现状一致。
2. **清理记录**：在文末既有「# 目录清理记录（2026-07-20）」（上一轮 ai_llama 清理）之后，追加了「# 目录清理记录（2026-07-20 补充）」段落，说明删除原因（Ollama 迁宿主机部署、容器化 entrypoint 脚本废弃）、删除前目录状态、备份归档路径，以及「全仓主文件已无可运行引用、凭证与 dev 运行配置不变、`docker compose config` 合并不受影响」的结论。

## 全仓残留引用排查结论

全仓（含所有主文件与子目录）Grep `docker-entrypoint-llama.sh` 与 `docker/ollama` 后，除下方**合理保留项**外，再无任何可运行引用（主 `docker-compose.base.yml` 未出现，确认服务已彻底移除）：

| 残留位置 | 类型 | 处置 | 说明 |
| --- | --- | --- | --- |
| `docker-compose.base.yml.a.bak-2026-07-20` / `.aligned.bak-` / `.llama.bak-` / `.safe.bak-2026-07-20` | 备份 compose | **保留（不改）** | 历史备份，仅注释示例提及 `entrypoint: /app/docker-entrypoint-llama.sh`，非运行期引用 |
| `docs/X-box-optimization-report-2026-07-14.md` | 历史报告 | **保留（不改）** | 早期优化报告树形描述，属历史记录 |
| `docs/deliverables/software-company/xbox-cleanup-ollama-script-2026-07-20.bak.sh` | 备份脚本归档 | **保留** | 本次清理产生的备份本体 |
| `项目开发说明`（「目录清理记录（2026-07-20 补充）」段落） | 文档清理记录 | **本次新增** | 描述性记录，非运行引用 |

> 注：全仓中仍存在的 `ollama` 字样（如 `scripts/setup-host-ollama.*`、`docker-compose.base.yml` 第 114 行宿主侧启动说明、后端 `application.yml` 的 `bge-m3:latest` 模型名）均指向**宿主机原生 Ollama 部署**，属当前正确架构，与本次删除的容器化脚本无关，未做任何改动。

## 变更影响与契约声明

- **凭证契约无需变更**：`.env.dev` / `.env.prod` 的真实密码、密钥、令牌值全程未读取、未改动。
- **dev 运行行为不变**：本次仅删除废弃脚本与空目录、更新文档，未触碰任何 dev 业务运行配置（compose 服务、env、脚本、后端代码均原样保留）。
- **合并 `docker compose config` 不受影响**：主 `docker-compose.base.yml` 早已移除 `dev-prj-llama` 容器服务，本次删除的脚本不在任何 compose / Dockerfile / env 中被引用，配置合并结果与删除前完全一致。

## 结论

本次为低风险、可逆（已备份）的废弃资产清理。操作范围严格限定于「无调用方的容器化 Ollama entrypoint 脚本及其空目录」，未影响任何运行配置与凭证。建议随下一次提交一并纳入版本管理。
