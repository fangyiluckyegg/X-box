# X-box 根目录废弃文件清理 · 交付报告

**日期**：2026-07-20
**团队**：software-cleanup-rootfiles（主理人齐活林 / 工程师寇豆码 / QA 严过关）
**模式**：快速模式（目录清理，非 git 提交）

## TL;DR
删除根目录 3 个历史废弃文件（Dockerfile.classphp.qa 临时 QA 副本、scan_secrets_tmp.py 一次性密钥扫描器、powershell 空文件），并同步更新 `项目开发说明` 4 处标注；QA 回归验证 docker compose 配置零回归。

## 删除清单（共 3 个）
| 文件 | 类型 | 删除理由 |
|---|---|---|
| `Dockerfile.classphp.qa` | QA TEMP COPY（Niu_Txl/Dockerfile.classphp 临时副本） | 头部注释声明临时副本；架构评审 R-13 明确建议删除；无 compose 引用 |
| `scan_secrets_tmp.py` | 一次性密钥扫描器（内容已全为 `<REDACTED>` 占位） | 文件自身声明 DO NOT COMMIT TO GIT；历史文档要求提交前删除；无业务代码引用 |
| `powershell` | 0 字节空文件 | 全项目无任何路径引用 |

## 项目开发说明 标注更新（4 处）
| 行（约） | 文件 | 修改 |
|---|---|---|
| 91 | docker-compose.business-prj.yml | `是否可删除？` → `# 保留：开发/内网业务编排（dev profile，与 base 共用基础设施）` |
| 93 | docker-compose.classphp.dev.yml | `可以考虑与基础业务容器合并` → `# 保留：dev 环境 PHP 容器编排（被 scripts/prj_restart.bat 引用）` |
| 94 | docker-compose.prod.yml | `是否可删除？` → `# 保留：生产环境编排（prod profile，外部化密钥）` |
| 95 | Dockerfile.classphp.qa | 整行删除（文件已删，下方 VERSION.md 行上移衔接） |

## QA 回归验证（严过关）
- `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config` → 退出码 **0**，STDERR 空
- 服务数 **7**（nginx-gateway / mysql / redis / prj-redis / prj-backend-c / prj-frontend / prj-php），端口 80/443/33060/63790/8081/1181 **各唯一**，prj-redis **无 host 映射**
- 3 个废弃文件**全部 NOT_EXIST**；保留文件（含 `Niu_Txl/Dockerfile.classphp`）完好
- 全仓活动引用搜索：3 文件名仅命中 `docs/` 历史报告，无活动代码/配置引用
- 开发说明标注更新核对：qa 字符串已删、3 处 compose 行均含"保留"
- **智能路由判定**：NoOne（全部通过）

## 文件清单
- 删除：`Dockerfile.classphp.qa`、`scan_secrets_tmp.py`、`powershell`
- 修改：`项目开发说明`
- 报告：`docs/deliverables/software-company/xbox-cleanup-rootfiles-2026-07-20.md`

## 遗留提示
- 本清理未执行任何 git 操作（按用户约定暂不提交），3 个文件显示为未提交删除态。
- 工作区累计多轮未提交改动，择机 `git add -A && git commit` 入库即可。
