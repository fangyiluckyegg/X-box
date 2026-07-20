# X-box 项目 ai_llama 废弃目录清理补充报告（2026-07-20）

> 执行时机：dev→prod 对齐 + 安全整合 + 方案A 三轮 QA 均通过后，执行的一次低风险废弃目录清理。
> 用户已明确确认执行本次清理。

## 一句话摘要
将 Ollama 容器版构建文件 `ai_llama/Dockerfile.llama` 与整个 `ai_llama/` 目录废弃删除，并将原 SSH 密钥安全说明文档归档至 `docs/security/ssh-key-incident.md`，同时清理 `docker-compose.base.yml` 中相关注释回退示例、更新 `项目开发说明`；全程未改动任何密码/密钥真实值与 dev 业务运行配置。

## 操作清单

| 模块 | 操作 | 文件:行号 | 改动要点 | 优先级 | 风险说明 |
| --- | --- | --- | --- | --- | --- |
| 安全文档归档 | 新增 | `docs/security/ssh-key-incident.md`（整文件） | 原 `ai_llama/README-SECURITY.md` 全文原样写入，顶部加一行归档注释 | 高 | 低风险：仅新增，未丢弃安全说明 |
| 废弃文件删除 | 删除 | `ai_llama/Dockerfile.llama` | 容器 Ollama 构建文件，Ollama 已迁宿主机，无任何实际 service 引用 | 高 | 低风险：删前确认无私钥、目录仅 2 文件 |
| 废弃目录删除 | 删除 | `ai_llama/`（目录） | ls 复核为空后 `rmdir` | 高 | 低风险：目录已确认无遗漏文件 |
| base.yml 注释清理 | 编辑 | `docker-compose.base.yml`（原第 117–150 行注释块 → 第 117–118 行清理注释） | 删除引用 `./ai_llama/Dockerfile.llama` 的「容器版 Ollama」注释回退块，替换为清理说明注释 | 中 | 低风险：仅删注释，未碰任何实际 service/volumes/platform |
| base.yml 备份 | 新增 | `docker-compose.base.yml.llama.bak-2026-07-20`（整文件） | 编辑前 `cp` 全量备份 | 中 | 低风险：仅副本 |
| 项目开发说明更新 | 编辑 | `项目开发说明`（文末第 146–150 行） | 追加「目录清理记录（2026-07-20）」说明块 | 中 | 低风险：仅追加，其余内容不变 |

## 归档说明
- **目标绝对路径**：`D:\crh123dexiaohao\X-box\docs\security\ssh-key-incident.md`
- **来源文件**：`D:\crh123dexiaohao\X-box\ai_llama\README-SECURITY.md`（原 SSH 密钥安全合规文档）
- **内容处理**：一字不差保留原文（含「当前状态 / 安全要求 / 操作步骤」三节），仅于文件顶部新增一行归档注释：
  > 本文档由原 ai_llama/README-SECURITY.md 于 2026-07-20 归档（Ollama 已迁宿主机部署，ai_llama 容器目录废弃移除）。
- **说明**：原文档记录「私钥曾进入 Git 历史、需重新生成密钥对并轮换、需用 git filter-repo/BFG 清理历史」等安全要求，按硬性约束「先归档、绝不丢弃」执行。

## 删除说明
- **删除文件**：`ai_llama/Dockerfile.llama`（1897 B，Ollama 容器推理服务构建文件）。
- **删除目录**：`ai_llama/`。
- **删前目录内容复核结果**：`ls -la ai_llama/` 显示目录内仅两个文件——`Dockerfile.llama` 与 `README-SECURITY.md`，**无任何 `id_ed25519` 私钥或其他文件**。先删除两个文件并再次 `ls` 确认目录为空，再执行 `rmdir` 移除目录。
- **删除后复核**：`ls` 确认 `ai_llama/` 已从项目根目录消失。

## base.yml 改动要点
- **备份路径**：`D:\crh123dexiaohao\X-box\docker-compose.base.yml.llama.bak-2026-07-20`（编辑前 `cp` 生成，与原文件内容一致）。
- **删除内容**：原第 117–150 行的「容器版 Ollama」注释回退示例块，含 `dockerfile: ./ai_llama/Dockerfile.llama`（原第 124 行）及其配套 `dev-prj-llama:` 注释服务定义。
- **替换内容**：第 117–118 行新增清理说明注释：
  ```yaml
  # 【清理·2026-07-20】移除「容器版 Ollama」注释回退示例：Ollama 已迁宿主机原生部署，
  # 对应构建文件随目录于本日删除，故不再保留该注释服务块（避免指向已删除的构建上下文）。
  ```
- **保留未动**：头部 `[Ollama 宿主原生迁移 2026-07-16]` 说明块（第 110–116 行）以及 mysql/redis/nginx/php 全部实际 service、entrypoint、volumes、platform 均未触碰。
- **校验**：编辑后全仓 Grep 确认主文件 `docker-compose.base.yml` 已不含 `ai_llama` 字样；YAML 结构完整（services → networks → volumes 顺序正常，文件可被正常解析）。

## 项目开发说明更新要点
- 经 Grep 确认，原文件**本就不含 `ai_llama` 树形条目**（任务假设的第 35–36 行 ai_llama 自问行在当前文件中不存在；实际「是否可以删除？」自问出现在 `web/prj-frontend/log`、`backend/ai-infer` 等节点，与 ai_llama 无关）。
- 为忠实记录本次清理且不臆造树节点，在文末追加「`# 目录清理记录（2026-07-20）`」四行说明（第 146–150 行），覆盖：ai_llama 目录废弃移除、安全文档归档位置、base.yml 注释回退示例移除、凭证与 dev 配置不变。其余内容一字未改。

## 全仓残留 ai_llama 引用排查结论
编辑后全仓 Grep `ai_llama`，主文件与文档中的**可运行引用均已清理**，剩余命中如下：

1. **保留·合理（备份文件，不修改）**：
   - `docker-compose.base.yml.llama.bak-2026-07-20:124`（本次备份）
   - `docker-compose.base.yml.a.bak-2026-07-20:123`
   - `docker-compose.base.yml.aligned.bak-2026-07-20:112`
   - `docker-compose.base.yml.safe.bak-2026-07-20:115`
2. **保留·合理（历史交付报告，不修改）**：
   - `docs/deliverables/software-company/xbox-prod-align-2026-07-20.md:56,63` — 历史对齐交付报告中的 Dockerfile 清单条目，属历史记录。
3. **保留·合理（归档后安全文档）**：
   - `docs/security/ssh-key-incident.md:1,9` — 即本次归档文档本身，原样保留原 README-SECURITY.md 中关于 ai_llama 目录的描述。
4. **文档更新（预期内，非可运行引用）**：
   - `项目开发说明:147-149` — 本次新增的「目录清理记录」说明。
5. **非可运行引用·描述性提及（低风险，按「不修改历史/不臆造」原则保留，不计入未清理项）**：
   - `docs/Mac-mini-deployment-guide.md:90` — 描述 `.dockerignore` 排除 `web/ai_llama/docs` 的构建上下文精简说明，为描述性文本，非指向已删 Dockerfile 的可运行引用；该排除路径对不存在目录无害。
   - `docs/code-review-report.md:4` — 历史代码审查范围中提及曾参考 `ai_llama/README-SECURITY.md`，为历史记录描述，非可运行引用。

**结论**：除以上保留项与描述性提及外，全仓**不再存在任何对 `ai_llama/Dockerfile.llama` 的可运行引用**（唯一可运行引用 `docker-compose.base.yml:124` 已移除）。

## 凭证与运行契约
- **凭证契约无需变更**：`.env.dev`、`.env.prod`（及 `.env.prod.example`、`.env.dev.example` 等）真实密码/密钥值均未被读取或修改。
- **dev 运行行为不变**：仅删除 ai_llama 容器构建文件与安全文档归档、清理 base.yml 中一处注释回退示例；mysql/redis/nginx/php 等实际 service、entrypoint、volumes、platform 及 dev 业务编排（`docker-compose.business-prj.dev.yml` 等）完全未动，dev 环境启动与运行机制与清理前完全一致。
