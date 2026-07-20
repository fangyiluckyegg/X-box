# X-box「ai_llama 废弃目录清理」— QA 独立复验报告

- **复验日期**：2026-07-20
- **复验人**：software-qa-engineer（严过关）
- **复验对象**：ai_llama 容器目录废弃清理（Ollama 推理自 2026-07-16 起迁宿主机原生部署）
- **环境**：Windows 11 + Git Bash；Docker 29.6.1（`docker compose` 可用）
- **方法**：只读验证，未修改任何业务文件；逐项给出 PASS/FAIL + 命令与输出证据。

---

## 验证项逐条结论

### 1. 目录已删除 + 无私钥遗漏 —— ✅ PASS

**命令与证据：**
```
# Glob **/ai_llama/**            → No files found
# Glob D:/.../X-box/ai_llama     → No files found
# find . -name 'id_ed25519*' -not -path './.git/*'  → (无输出, EXIT=0)
# git status --porcelain | grep ai_llama
 D ai_llama/Dockerfile.llama
 D ai_llama/README-SECURITY.md
# Grep id_ed25519 (全仓) → 仅文档文本命中：
#   docs/security/ssh-key-incident.md（归档说明）
#   docs/deliverables/software-company/xbox-cleanup-ai_llama-2026-07-20.md（交付报告）
#   均非实际私钥文件
```

**结论**：`ai_llama/` 目录及 `Dockerfile.llama`、`README-SECURITY.md` 已删除；项目根不存在 `id_ed25519*` 私钥文件；文本命中仅为归档/报告说明，无密钥泄露。

---

### 2. 安全文档已归档 —— ✅ PASS

**证据**（读取 `docs/security/ssh-key-incident.md`）：
- 第 1 行归档注释：`> 本文档由原 ai_llama/README-SECURITY.md 于 2026-07-20 归档（Ollama 已迁宿主机部署，ai_llama 容器目录废弃移除）。`
- 关键字命中：
  - `id_ed25519`（第 7、21、24 行）
  - `git filter-repo` / `BFG Repo-Cleaner`（第 15 行）
  - `CI/CD Secret`（第 13 行："生产环境：私钥不得存在于项目目录中，必须通过 CI/CD Secret 或运维配置管理工具注入"）

**结论**：原 SSH 密钥安全说明全文归档，含全部要求关键字，顶部含 2026-07-20 归档注释。

---

### 3. base.yml 可运行引用已清零 —— ✅ PASS

**证据**：
```
# Grep ai_llama 于 docker-compose.base.yml → No matches found (0 命中)
# docker-compose.base.yml 关键行：
  redis:
    image: redis:7-alpine          # 符合预期，未回退到 5.x
  # 原 dev-prj-llama 注释服务块（含 dockerfile: ./ai_llama/Dockerfile.llama）
  # 已在 diff 中整段删除（35 行删除），替换为清理注释。
```

- **mysql / redis / nginx-gateway 实际 service、entrypoint、volumes、platform 完好**：
  - `nginx-gateway`：volumes(3)、ports(80/443)、networks 完整。
  - `mysql`：`env_file(.env.dev)`、`image: mysql:8.0`、`volumes(mysql_data/mysql_init/mysql_scripts/logs)`、`entrypoint(wrapper)`、`command` 完整。
  - `redis`：`image: redis:7-alpine`、`volumes`、`command(--requirepass ${REDIS_PASSWORD:-...})`、`ports(63790)` 完整。
- 注：base.yml 同时含 prod-align 的 `redis:5→7` 升级注释与 方案A 的 class_init 说明注释（见第 7 项补充），但 **ai_llama 相关注释回退块已彻底移除**。

**结论**：base.yml 主文件已无 `ai_llama` 字样；常驻服务配置完好；redis 为 7-alpine。

---

### 4. 合并配置仍合法 —— ✅ PASS

**命令**：
```
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config
# EXIT_COMPOSE=0
# STDERR: 仅 "aJs variable is not set. Defaulting to a blank string." 警告（非 error）
```

**证据**：
```
# 服务清单（7 个，与要求完全一致）：
  nginx-gateway  mysql  redis  prj-redis  prj-backend-c  prj-frontend  prj-php
# （另含 dev-network、mysql_data，为网络/卷，非服务）

# 发布端口各只一次：
  published: "80"      (1)
  published: "443"     (1)
  published: "33060"   (1)
  published: "63790"   (1)
  published: "8081"    (1)
  published: "1181"    (1)

# prj-redis 端口映射检查：
  grep -A30 '^  prj-redis:' config | grep -E 'ports:|published:' → 无命中
  → prj-redis 无宿主端口映射 ✅
```

**结论**：合并配置解析通过（exit 0，仅无害变量告警）；服务恰为 7 个；6 个发布端口各唯一；prj-redis 无宿主端口映射。清理未破坏编排。

---

### 5. 全仓可运行引用排查 —— ✅ PASS

**证据**（`Grep ai_llama` 全仓；`Grep ai_llama/Dockerfile.llama` 全仓）：

命中文件**全部为合理保留项**：
| 文件 | 类别 | 是否保留 |
|------|------|----------|
| `docker-compose.base.yml.llama.bak-2026-07-20` 等 `*.bak-*` | 备份文件 | ✅ 保留 |
| `docs/deliverables/software-company/xbox-cleanup-ai_llama-2026-07-20.md` | 历史交付报告 | ✅ 保留 |
| `docs/deliverables/software-company/xbox-prod-align-2026-07-20.md` | 历史交付报告 | ✅ 保留 |
| `项目开发说明` | 清理记录章提及（合理） | ✅ 保留 |
| `docs/Mac-mini-deployment-guide.md` | 描述性历史提及 | ✅ 保留 |
| `docs/code-review-report.md` | 描述性历史提及 | ✅ 保留 |
| `docs/security/ssh-key-incident.md` | 归档安全文档 | ✅ 保留 |

**关键**：以上命中均**不含任何 compose 主文件、Dockerfile 或运行脚本**。活动编排文件（`docker-compose.base.yml` / `docker-compose.prod.yml` / `docker-compose.business-prj.yml` / `docker-compose.business-prj.dev.yml`）及 `scripts/` 下脚本经全仓递归检索，**均未引用 ai_llama**。

**结论**：除合理保留项外，全仓无任何 compose 主文件或运行脚本再引用 `ai_llama` / `ai_llama/Dockerfile.llama`。

---

### 6. 无密码/密钥篡改 —— ✅ PASS（附观察）

**证据**：
```
# git status --porcelain 不含 .env.prod、不含 .env.dev
# （仅 .env.dev.example 为 M，属并行 prod-align 任务，为例文件非真实凭证）
# base.yml 改动限于删除注释块 + 注释补充；
#   redis 认证：command: redis-server --requirepass ${REDIS_PASSWORD:-redis_default_pass_change_me}
#   → 使用变量 + 占位默认值，非真实明文密码
```

**观察（超出本清理范围，非本验证引入）**：`docker-compose.prod.yml` 的 `prj-redis` 服务以明文 `--requirepass <REDACTED-live-prod>` 写入真实 Redis 密码，且该文件改动来自并行任务 prod-align（非本次清理）。此与 `docs/security/ssh-key-incident.md` 中"生产环境密钥应通过 CI/CD Secret 注入、不放入项目目录"的要求相悖，**建议后续单独跟进**，但不影响本清理项判定。

**结论**：清理未篡改 `.env.prod`/`.env.dev` 真实密码，未引入任何明文密码。

---

### 7. 改动范围核验 —— ✅ PASS（附补充说明）

**证据**（`git status --porcelain` 节选）：
```
 D ai_llama/Dockerfile.llama
 D ai_llama/README-SECURITY.md
 M docker-compose.base.yml
 M "项目开发说明"
?? docs/security/
?? docker-compose.base.yml.llama.bak-2026-07-20
# （另含 prod-align / 方案A 并行任务改动，见补充说明）
```

- ai_llama 相关为删除：✅（`Dockerfile.llama`、`README-SECURITY.md` 已 `D`）
- ssh-key-incident.md 新增：✅（`?? docs/security/`）
- base.yml 改动：✅（含 ai_llama 注释块删除；`git diff` 另显 prod-align 的 `redis:5→7` 与 方案A 的 class_init 注释，属并行任务）
- 项目开发说明改动：✅（`tail` 核验文末追加「目录清理记录（2026-07-20）」，含 ai_llama 目录移除、安全文档归档、base.yml 注释块移除、凭证未变动等说明）
- base.yml.llama.bak 备份存在：✅（`?? docker-compose.base.yml.llama.bak-2026-07-20`，内容仍含 `ai_llama/Dockerfile.llama` 引用，确为清理前备份）
- dev 业务运行配置未回退：`docker-compose.business-prj.dev.yml` 虽为 `M`，但来自并行 prod-align 任务（dev↔prod 对齐），合并配置仍 exit 0 通过，非清理所致回退。

**补充说明（重要，非清理 bug）**：工作区除本清理改动外，还叠加了并行未提交任务 **prod-align** 与 **方案A** 的改动，故 `git status` 显示文件多于任务描述的"仅 3 个文件"。具体并行改动（非本清理范围）：
- `.env.dev.example`(M)、`db/class_init/*`(D) + `db/mysql_init/*`(??)
- `docker-compose.business-prj.yml`(M)、`docker-compose.business-prj.dev.yml`(M)、`docker-compose.prod.yml`(M, 47/66)、`web/prj-frontend/Dockerfile.prod`(M)
- 多个 `*.bak` 与交付报告（`xbox-prod-align-*.md` 等）

上述改动均不影响本清理判定，且合并配置仍合法通过。

---

## 全仓 ai_llama 残留排查结论

全仓对 `ai_llama` 与 `ai_llama/Dockerfile.llama` 的引用仅存在于：**备份文件（`*.bak-*`）**、**历史交付报告（`docs/deliverables/software-company/*.md`）**、**描述性历史文档**（`docs/Mac-mini-deployment-guide.md`、`docs/code-review-report.md`）、**归档安全文档**（`docs/security/ssh-key-incident.md`）、以及**项目开发说明的清理记录章**。所有活动编排文件（base/prod/business compose）、Dockerfile、运行脚本均不再引用 `ai_llama`。**无遗留可运行引用**。

---

## 最终结论

| 项目 | 结果 |
|------|------|
| 1. 目录已删除 + 无私钥遗漏 | ✅ PASS |
| 2. 安全文档已归档 | ✅ PASS |
| 3. base.yml 可运行引用清零 | ✅ PASS |
| 4. 合并配置仍合法 | ✅ PASS |
| 5. 全仓可运行引用排查 | ✅ PASS |
| 6. 无密码/密钥篡改 | ✅ PASS |
| 7. 改动范围核验 | ✅ PASS |

- **是否发现配置 bug**：否。
  - base.yml 已无 ai_llama 引用 ✅
  - 合并 config exit 0、无 error ✅
  - redis 仍为 7-alpine（符合预期，非回退）✅
  - 未删错私钥文件 ✅
  - .env.prod 未被清理改动 ✅
- **路由判定**：**NoOne**（全部 PASS，无需转交 Engineer 修复）。

### 遗留观察（超出清理范围，建议单独跟进，不影响本次通过）
1. 工作区叠加 prod-align / 方案A 并行未提交改动，`git status` 比任务描述更广——已说明，非清理 bug。
2. `docker-compose.prod.yml` 的 `prj-redis` 明文 Redis 密码，与归档安全文档的密钥管理要求相悖，建议后续改为 CI/CD Secret 注入。
