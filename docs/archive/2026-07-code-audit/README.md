# 历史归档：X-box 代码审计与整改方案

> 归档时间：2026-07-12
> 原路径：`deliverables/software-company/code-audit/`
> 归档原因：项目进入微优化阶段，将原代码审计产出从 `deliverables/`（交付物暂存区）迁移至 `docs/archive/`，作为历史上下文长期保留，不再参与日常交付物清单。

## 本目录文件

| 文件 | 类型 | 内容 |
|---|---|---|
| `comprehensive-remediation-plan.md` | 整改计划 | X-box 从代码审计到修复的完整方案：T01–T20 整改任务、优先级、涉及文件、测试矩阵、设计决策（含 C1/C16/C17、[P0-FIX]/[P1-15-FIX] 等历史修复标注的来由）。 |
| `class-diagram.mermaid` | 类图 | 领域模型（user_info / EmployeeKpi / LoginUser 等）及关系，Mermaid 语法，可粘贴至支持 Mermaid 的编辑器渲染。 |
| `sequence-diagram.mermaid` | 时序图 | 关键调用链路（登录鉴权、CompareController 向量比对）的时序，Mermaid 语法。 |

## 与其他产物的关系

- 代码中的历史修复标注（`[C1]`/`[C16]`/`[C17]`/`[P0-FIX]` 等）均在此整改计划中有对应条目与背景。
- 部署文档 `docs/部署上线方案.md` 第 5.2 节（存量库迁移）、第 9 章（环境坑）与此处的整改背景互补。
- 基础设施脚本 `db/mysql_init/migrate_role.sql`、`db/mysql_scripts/docker-entrypoint-wrapper.sh` 的"为何存在"可在此找到原始问题定义。

## 查阅建议

- 日常维护 / 交接时优先看 `comprehensive-remediation-plan.md`，比翻 git log 更快理解"为什么这么改"。
- 类图 / 时序图用支持 Mermaid 的 Markdown 预览器打开即可渲染。
- 本目录为只读历史档案，不再更新；若后续有新的整改，请在 `docs/` 下新建文档、勿回写此处。
