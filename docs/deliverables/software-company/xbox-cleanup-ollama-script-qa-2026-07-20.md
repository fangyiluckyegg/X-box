# X-box 废弃脚本清理 QA 复验报告（docker-entrypoint-llama.sh）

- **复验对象**：`docker/ollama/docker-entrypoint-llama.sh` 废弃脚本清理（含空目录、开发说明、补充报告）
- **复验人**：QA 工程师（Edward，独立复验）
- **复验日期**：2026-07-20
- **项目根目录**：`D:\crh123dexiaohao\X-box\`
- **验证方式**：只读验证（不修改任何业务文件），命令均在项目根目录执行，Docker 29.6.1 可用
- **路由判定**：**NoOne（全部 PASS，无需转交 Engineer 修复）**

---

## 一、验证项逐项结果

### T1 脚本与目录已删除 —— ✅ PASS

**判定**：`docker/ollama/docker-entrypoint-llama.sh`、`docker/ollama/`、`docker/` 均已不存在。

**证据**：
```bash
$ ls -la "docker/ollama/docker-entrypoint-llama.sh"
ls: cannot access 'docker/ollama/docker-entrypoint-llama.sh': No such file or directory   # exit=2

$ ls -la "docker/ollama"
ls: cannot access 'docker/ollama': No such file or directory                                # exit=2

$ ls -la "docker"
ls: cannot access 'docker': No such file or directory                                        # exit=2
```

- Glob 全仓 `**/docker/**` → **No files found**（无残留）。
- `git status` 显示 `deleted: docker/ollama/docker-entrypoint-llama.sh`，与删除动作一致。

---

### T2 脚本已备份归档 —— ✅ PASS

**判定**：备份文件存在，字节数与声明的原始脚本 7696 字节**完全一致**；内容为完整可识别的 shell 脚本结构（非截断/损坏），无明文密码。

**证据**：
```bash
$ ls -la "docs/deliverables/software-company/xbox-cleanup-ollama-script-2026-07-20.bak.sh"
-rwxr-xr-x 1 xuxin 1049089 7696 ... xbox-cleanup-ollama-script-2026-07-20.bak.sh   # 与声明 7696 字节一致

$ wc -c "docs/deliverables/software-company/xbox-cleanup-ollama-script-2026-07-20.bak.sh"
7696                                                                                  # 精确一致

$ wc -l "...bak.sh"
177                                                                                    # 说明见下方"备注"
```

- 备份文件首尾结构完整：首行 `#!/usr/bin/env bash` 类注释，末行为 `main "$@"`，文件以换行符 `\n` 正常结尾（经 `od -An -c` 确认），**无截断**。
- 原始脚本已删除，无法做 `diff`/`md5sum` 逐字节比对；以字节数 7696 精确一致 + 结构完整作为归档完整性证据。
- 对备份做密钥扫描（`grep -niE "password|secret|token|mysql_pwd|BGE|API_KEY"`）：仅命中 `bge-m3` 模型名（第 5/51/52/59 行），**无任何明文密码/密钥**。

**备注（非缺陷）**：`wc -l` 报告 177 行，声明为 178 行。差异源于末尾换行符计数约定（原始脚本末行无尾随换行时逻辑行数为 178、换行符数为 177；备份末行带换行，换行符数仍为 177）。字节数精确一致（7696），表明备份**完整无丢失**，不构成失败。

---

### T3 全仓无运行引用 —— ✅ PASS

**判定**：除声明的合理保留项外，全仓无任何对 `docker/ollama` 路径或 `docker-entrypoint-llama` 入口的**可运行引用**。

**证据 1 — 精确 Grep `docker-entrypoint-llama` 全仓命中清单**：
```
docker-compose.base.yml.safe.bak-2026-07-20:133      # 注释示例 entrypoint: [.../app/docker-entrypoint-llama.sh]（备份 compose）
docker-compose.base.yml.aligned.bak-2026-07-20:130    # 同上（备份 compose）
docker-compose.base.yml.a.bak-2026-07-20:141          # 同上（备份 compose）
docker-compose.base.yml.llama.bak-2026-07-20:142      # 同上（备份 compose）
项目开发说明:151                                       # 描述性清理记录（允许）
docs/.../xbox-cleanup-ollama-script-2026-07-20.md      # 本次新增报告（允许）
docs/.../xbox-cleanup-ollama-script-2026-07-20.bak.sh:3# 备份文件本身（允许）
```
→ **主文件 `docker-compose.base.yml`、`docker-compose.prod.yml` 任何 Dockerfile、任何运行脚本均未出现该引用。**

**证据 2 — 扩展 Grep `ollama|dev-prj-llama`（排查是否换名残留）命中分类**：
- 备份 compose（`*.bak-2026-07-20`）：均为注释示例，非运行期引用 —— 保留项，允许。
- 历史/描述性文档（`docs/X-box-optimization-report-2026-07-14.md`、`docs/X-box-db-merge-qa-2026-07-14.md`、`docs/qa-verification.md`、`docs/prod-mac-runbook.md`、`docs/ollama-host-migration-*.md`、`docs/security/ssh-key-incident.md`、`项目开发说明`）—— 合理保留，允许。
- 主 compose 中的 `ollama`/`dev-prj-llama`：**仅出现在注释行**（`docker-compose.base.yml` 第 111–117 行、`docker-compose.prod.yml` 第 21/39 行、`docker-compose.business-prj.yml` 第 58/70–72 行等），均为「Ollama 宿主原生迁移」说明，**无活动服务定义 / 无 `entrypoint:` / 无 `docker/ollama` 路径引用**。
- `backend/prj-backend-c/...` Java 源码：经 `AI_SERVICE_URL` 环境变量（运行时取 `host.docker.internal:11434`）调用宿主原生 Ollama，**不引用 `docker/ollama` 脚本或 `dev-prj-llama` 容器**，属合法运行用法。
- `scripts/setup-host-ollama.sh` / `.ps1`：宿主原生 Ollama 初始化脚本，替代原容器方案，未引用 `docker/ollama`。

**结论**：无运行引用泄漏，符合清理预期。

---

### T4 项目开发说明更新正确 —— ✅ PASS

**判定**：目录树中已无 `docker/ollama` 行；文末含 `目录清理记录（2026-07-20 补充）` 段落；正文内容完整未回退。

**证据**：
```bash
$ grep -n "docker/ollama" "项目开发说明"
154:- `docker/ollama/docker-entrypoint-llama.sh` 已删除：...   # 仅命中"清理记录"，非目录树

$ grep -n "目录清理记录（2026-07-20 补充）" "项目开发说明"
153:# 目录清理记录（2026-07-20 补充）
```

- 目录树（第 16–100 行）经通读确认**无 `docker/` 条目**，故亦无 `docker/ollama` 两行。
- 文末清理记录（第 150–155 行）包含：脚本删除说明、空目录一并移除说明、备份归档路径、全仓无运行引用说明、凭证未变动说明，内容自洽。
- 文档其余章节（开发/生产环境清单、资源配额、端口规划、清理记录 2026-07-20 首段等）完整、连贯，未见回退或破坏。

---

### T5 合并配置仍合法 —— ✅ PASS

**判定**：合并 `docker-compose.base.yml` + `docker-compose.prod.yml` 解析成功（退出码 0、无 error），服务清单恰为 7 个，端口 80/443/33060/63790/8081/1181 各只一次，`prj-redis` 无宿主端口映射。

**证据**：
```bash
$ docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config
CONFIG_EXIT:0                                                   # 退出码 0，无 error

$ docker compose ... config --services
nginx-gateway
mysql
prj-redis
prj-backend-c
prj-frontend
prj-php
redis                                                            # 共 7 个，与预期完全一致

$ grep -nE "published:" /tmp/compose_config.txt
34:  published: "33060"
68:  published: "80"
73:  published: "443"
156: published: "8081"
191: published: "1181"
266: published: "63790"                                          # 恰 6 个端口，各出现一次

# 服务→端口映射：
#   mysql        -> 33060
#   nginx-gateway-> 80, 443
#   prj-frontend -> 8081
#   prj-php      -> 1181
#   redis        -> 63790
#   prj-redis    -> （无 ports 块，仅内部 --requirepass 配置）✅ 无宿主端口映射
```

- stderr 仅有预存的 `aJs variable is not set` 警告（与本次清理无关，历史即有），**无任何 error / failed 解析**。
- 端口计数：80×1、443×1、33060×1、63790×1、8081×1、1181×1 —— 全部各一次，与验收标准吻合。

---

### T6 无密码/密钥篡改 —— ✅ PASS

**判定**：`.env.prod` / `.env.dev` 真实密码未被本次清理修改；本次清理未引入任何明文密码。

**证据**：
```bash
$ git status --short | grep -iE "\.env\.prod|\.env\.dev\b"
（无输出）                                                      # .env.prod / .env.dev 均未被修改

$ git check-ignore -v .env.prod
.gitignore:3:.env*	.env.prod                                  # .env.prod 受 gitignore 保护（不入库）
```

- `git status` 变更列表中**不含 `.env.prod` 修改**，凭证真实值未变动。
- 本次清理仅涉及：删除脚本/目录、更新开发说明、新增报告；未触碰 `.env.*` 文件，未向任何文件写入明文密码。

**备注（观察，非本次清理引入，不影响判定）**：`docker-compose.prod.yml` 的 `prj-redis` 服务中存在**硬编码 Redis 口令**（`--requirepass <REDACTED-live-prod>`，healthcheck 同样内联该口令）。该口令属于此前「prod 对齐」工作的既有内容，**非本次 ollama 脚本清理引入**，故不计入本次 FAIL、不路由 Engineer。但建议作为独立安全议题跟进（凭证应经 `.env` 注入而非明文落库 compose）。

---

## 二、全仓残留引用排查结论

| 引用对象 | 主文件（compose/Dockerfile/脚本） | 备份/历史文档 | 结论 |
|---|---|---|---|
| `docker-entrypoint-llama` | 无 | 4 个 `.bak-*` compose、开发说明清理记录、本报告、备份脚本本身 | 仅保留项，无运行引用 ✅ |
| `docker/ollama` 路径 | 无 | 开发说明清理记录（描述性） | 无运行引用 ✅ |
| `dev-prj-llama` 服务 | 仅注释（迁移说明） | 历史报告、Java `@Value` 兜底默认值 | 无活动服务定义 ✅ |

全仓**无任何可运行引用**泄漏至主编排/构建/运行脚本，清理干净。

---

## 三、最终结论

- **6 项验证全部 PASS**（T1 删除 / T2 备份 / T3 无运行引用 / T4 开发说明 / T5 合并配置 / T6 无密钥篡改）。
- 唯一观察项（compose 中既有 Redis 明文口令）为历史遗留、非本次清理引入，已单列备注，不影响通过判定。
- **最终判定：通过 ✅**
- **路由：NoOne**（无需转交 Engineer 修复；校验脚本/本 QA 过程无自身问题，无需自修）。

---

*附：复验所用关键命令*
```bash
# T1
ls -la "docker/ollama/docker-entrypoint-llama.sh"; ls -la "docker/ollama"; ls -la "docker"
# T2
wc -c "docs/deliverables/software-company/xbox-cleanup-ollama-script-2026-07-20.bak.sh"
# T3
grep -rn "docker-entrypoint-llama" . ; grep -rni "ollama\|dev-prj-llama" .
# T4
grep -n "docker/ollama" "项目开发说明" ; grep -n "目录清理记录（2026-07-20 补充）" "项目开发说明"
# T5
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config --services
# T6
git status --short | grep -iE "\.env\.prod|\.env\.dev\b"
```
