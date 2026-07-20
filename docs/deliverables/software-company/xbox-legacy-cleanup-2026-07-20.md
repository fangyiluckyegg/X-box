# X-box 遗留项清理交付报告（2026-07-20）

> 主理人：齐活林（Qi）｜团队：`software-legacy-cleanup`
> 范围：推进四项历史遗留项中的可执行三项（frpc 标注清理、.gitattributes 固化、.env.prod 转义）；prj-redis 密码项经核查已合规，未改动。
> 注意：**未执行任何 git 操作**（用户要求不提交）。

## 一、遗留项处理结论

| # | 遗留项 | 核查结论 | 本次动作 |
|---|---|---|---|
| 1 | `frpc.class-http.example.toml` 标注 | Glob 全项目确认该文件**已不存在**（早于本流程被删） | 删除 `项目开发说明` 第 63 行失效标注 `frpc.class-http.example.toml   # 是否可以删除？` |
| 2 | prj-redis 明文密码 | compose 中实为 `${REDIS_PASSWORD}` 变量引用（docker-compose.prod.yml:62/64/96），密码值仅在 `.env.prod`（已被 gitignore，不入库） | **无需改文件**，已非硬编码，符合基本安全要求；彻底去盘依赖 CI/CD Secret 注入，属部署侧优化 |
| 3 | `.gitattributes` 缺 yml/yaml 规则 | 原仅有 `*.sh text eol=lf` | 追加 `*.yml text eol=lf`、`*.yaml text eol=lf`、`.env* text eol=lf` |
| 4 | `.env.prod` 的 `$aJs` 插值告警 | 第 12 行 `MYSQL_ROOT_PASSWORD=...$aJs` 被 compose 当变量插值清空 | 转义为 `$$aJs`，保留密码字面量 |

## 二、QA 回归验证（严过关）

- `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config` → **EXIT=0**
- **`aJs variable is not set` 告警：Before 5 条 → After 0 条** ✅
- 服务数 = 7（nginx-gateway / mysql / redis / prj-redis / prj-backend-c / prj-frontend / prj-php）；端口 80/443/33060/63790/8081/1181 各唯一；prj-redis 无 host 映射
- `frpc.class-http.example.toml` 全仓（排除 .git/docs）检索无引用
- `.gitattributes` 三行新增规则在位，原 `*.sh` 规则未动
- `.env.prod` 第 12 行仅 `$aJs`→`$$aJs` 一处变化，密码其余字符一致
- 工程师未产生任何 git commit（三处改动均为未提交工作树修改）

**智能路由判定：NoOne（全部通过）**

## 三、文件清单

| 动作 | 路径 |
|---|---|
| 修改 | `项目开发说明`（删除 frpc 标注行） |
| 修改 | `.gitattributes`（追加 3 行 LF 固化规则） |
| 修改 | `.env.prod`（第 12 行 `$aJs` → `$$aJs` 转义） |

## 四、用户下一步建议

1. **收敛工作区（仍待做）**：本次及前八轮改动均未提交，建议择机 `git add -A && git commit` 一次性入库（用户本轮明确要求暂不提交）。
2. **prj-redis 彻底去盘（部署侧）**：若需彻底不落盘密码，可在 CI/CD 中以 Secret 注入 `REDIS_PASSWORD`，本地 `.env.prod` 仅保留于开发机；当前变量引用方案已满足"非硬编码"安全要求。
3. **`$aJs` 已修复**：MySQL root 密码现可完整保留字面量，无需再关注插值告警。
4. **`.gitattributes` 生效提示**：新增规则对**后续 checkout/commit** 生效；已入库的 yml 若此前为 CRLF，需 `git add --renormalize .` 才会统一转 LF（可选，非必须）。
