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
- `docs/architecture_review.md:181` — 历史架构评审报告
- `docs/deliverables/software-company/xbox-cleanup-logs-2026-07-20.md` — 本轮清理报告（文档说明性文字，非运行引用）

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
- 本轮新增报告 `docs/deliverables/software-company/xbox-cleanup-logs-2026-07-20.md` 扫描 secret 关键词（password / secret / token / 密钥 / 口令 / MYSQL_ROOT_PASSWORD / api_key 等）：仅命中文档性陈述（"无密钥/token 等敏感文件"、"未读取、未修改任何密码、密钥、令牌真实值"），**无任何明文密码/密钥真实值**。
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
- 本轮 logs 清理的 git 影响：删除的是未纳入版本控制的空目录（git 不跟踪空目录，故 porcelain 中无对应 `D` 记录），以及新增报告文件 `?? docs/deliverables/software-company/xbox-cleanup-logs-2026-07-20.md`。日志清理本身未修改任何已跟踪文件。
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
