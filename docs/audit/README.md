# 审计归档索引（Audit Archive）

本目录汇总 X-box 历次安全 / 清理 / 代码审计的归档材料，按时间线与主题分置于 `archive/` 下。

## 归档结构

| 子目录 | 内容说明 |
|---|---|
| `archive/2026-07-14/` | 2026-07-14 旧一轮审计日志（15 个）：P0 / P1 安全整改、数据库合并、优化报告、后端构建修复等原始 `log` 与 `qa` 复验，整批迁移、保留 `log` / `qa` 原结构，**不合并**。 |
| `archive/2026-07-20/` | 2026-07-20 清理交付物（16 个）：xbox 容器化清理与 prod 对齐的交付报告。其中 7 对 `report` + `qa` 已合并为单文件（`<short>.md`，以 `---` 分隔并附 `## QA 复验`），其余单文件以短名保留；含 ollama 脚本备份 `xbox-cleanup-ollama-script-2026-07-20.bak.sh`。 |
| `archive/2026-07-code-audit/` | 2026-07 代码审计（4 个）：类图、时序图与综合整改方案。原位于 `docs/audit/archive/`，已于 2026-07-20 文档整理时并入 `docs/audit/archive/` 体系。 |

## 说明

- 所有归档均为**只读历史材料**，仅供追溯与交接查阅，不再参与日常交付物清单。
- 密钥相关字段已在 `security/prod-secret-history-cleanup.md` 及 2026-07-20 相关报告中脱敏，统一以占位符 `<REDACTED-live-prod>` 标记。
- 已废弃的 `compare_refactor_*` 并行重构提案与 `xbox-cleanup-quarantine-2026-07-20.bak`（旧 Niu_Txl PHP 禁用副本）已按决策删除，不在本归档内。
