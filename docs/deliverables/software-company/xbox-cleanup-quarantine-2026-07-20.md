# X-box · 清理 Niu_Txl/_quarantine 隔离目录 — 交付报告

- 日期：2026-07-20
- 项目：X-box 容器化（dev: Windows/Intel，prod: macOS M2 ARM64/OrbStack）
- 任务类型：废弃目录清理（快速模式）
- 工作流：TeamCreate → 工程师(备份+删除+文档) → QA(回归验证)
- 团队：`software-cleanup-quarantine`

## TL;DR
已安全删除 7 月 14 日安全整改遗留的 `Niu_Txl/_quarantine` 废弃隔离目录，先完整备份后删除并同步更新开发说明清单，QA 回归验证 docker compose 配置零回归。

## 交付概览
| 项 | 结果 |
|---|---|
| 交付状态 | ✅ 完成 |
| 测试通过率 | 100%（QA 7 项检查全 PASS） |
| 智能路由判定 | NoOne（全部通过，无源码/测试 Bug） |
| 已知问题数 | 0（删除操作无回归） |

## 执行步骤与产物

### 1. 备份（工程师）
- 备份路径：`docs/deliverables/software-company/xbox-cleanup-quarantine-2026-07-20.bak/`
- 方式：`cp -r` 完整复制（保留目录结构与全部文件/子目录）
- 核对：文件数 = 21（与原目录一致）、目录数 = 7（一致）
- 顶层内容：`.htaccess`、`_mmServerScripts_902_message.disabled`、`_mmServerScripts_902_work.disabled`、`607_copies`、`607_uploadHandler.hardened.php.txt`、`607_uploadHandler.php.disabled`、`902_fupaction.php.disabled`、`902_work_copies`、`info.php.disabled`

### 2. 删除（工程师）
- 命令：`rm -rf Niu_Txl/_quarantine`
- 确认：目录已不存在（QA `test -d` 返回非 0）

### 3. 文档更新（工程师）
- 文件：`项目开发说明`
- 修改：删除原第 58 行 `│   ├── _quarantine          # 是否可以删除？`
- 现状：`Niu_Txl` 目录树下仅剩 `607`、`902` 两个业务子目录
- 编码：UTF-8 + CRLF，保持不变，未破坏其他内容

## QA 回归验证（严过关）
| 验证项 | 命令 | 结果 |
|---|---|---|
| docker compose 配置解析 | `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config` | EXIT 0 ✅ |
| 目录删除确认 | `test -d Niu_Txl/_quarantine` | 已不存在 ✅ |
| 引用搜索 | `grep -rnE "Niu_Txl/_quarantine|_quarantine"` | 仅 docs/ 命中 ✅ |
| 备份目录确认 | `test -d docs/deliverables/...bak` | 存在 ✅ |

- 服务数 = 7：`nginx-gateway`、`mysql`、`redis`、`prj-redis`、`prj-backend-c`、`prj-frontend`、`prj-php`
- 端口唯一性：80/443/33060/63790/8081/1181 各出现一次，无冲突
- `prj-redis` 无 host 端口映射（符合设计）
- 活动配置（compose / .env）仅引用 `Niu_Txl` 父目录，无 `_quarantine` 挂载

> 注：compose config 有一条无害 warning（`aJs` 变量未在 .env.prod 设置，默认置空），属历史遗留配置问题，与本次删除无关。

## 文件清单
| 动作 | 路径 |
|---|---|
| 删除 | `Niu_Txl/_quarantine/`（原 21 文件 / 7 目录） |
| 新增（备份） | `docs/deliverables/software-company/xbox-cleanup-quarantine-2026-07-20.bak/` |
| 修改 | `项目开发说明`（移除 `_quarantine` 清单行） |

## 用户下一步建议
1. 若需还原隔离文件，从 `docs/deliverables/software-company/xbox-cleanup-quarantine-2026-07-20.bak/` 移回 `Niu_Txl/_quarantine/` 即可（原件完整保留）。
2. **建议 commit 本轮及前 7 轮累计的未提交改动**：`git add -A && git commit -m "chore: 清理废弃目录与配置对齐"`。
3. 遗留项（历史，非本次引入）：`docker-compose.prod.yml` 中 prj-redis 明文密码建议迁移至 CI/CD Secret。
4. 建议新增 `.gitattributes`（`*.yml/*.yaml/.env* text eol=lf`）固化换行，避免 Windows/ARM64 间 CRLF 差异。
5. `.env.prod` 中 `aJs` 变量请确认是否仍需（当前 warning 来源）。
