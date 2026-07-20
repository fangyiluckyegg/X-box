# X-box 文档目录迁移报告：deliverables/ → docs/deliverables/

> 报告日期：2026-07-20
> 执行人：software-engineer（寇豆码）
> 关联阶段：dev→prod 对齐、安全整合、方案A、ai_llama 清理 四轮 QA 通过后的低风险文档整理

## 一、一句话摘要

将根级 `deliverables/` 整体（13 个文件）经 `git mv` 迁移至 `docs/deliverables/`（保留 git 历史、改动最小），并同步更新所有内部交叉引用，全仓已无指向根级 `deliverables/` 的有效（可导航）引用。

## 二、操作清单

| 模块 | 操作 | 文件:行号 | 改动要点 | 优先级 | 风险说明 |
|---|---|---|---|---|---|
| 目录迁移 | `git mv` | `deliverables/` → `docs/deliverables/` | 整体迁移 13 个文件；已跟踪文件（4 个 compare_refactor + 1 个 xbox-delivery-2026-07-16）识别为 rename 保留历史；8 个新报告先 `git add` 后随目录一并移动 | P0 | 低风险；已校验根级 `deliverables/` 消失、`docs/deliverables/` 出现且文件数一致（13） |
| `.dockerignore` | 改规则 | `.dockerignore:9` | 原 `deliverables/` 排除项改为注释：`deliverables/ 已于 2026-07-20 整体迁移至 docs/deliverables，已由上方 docs/ 规则排除，无需单独排除` | P1 | 极低风险；旧规则仅排除 docker 构建上下文，语义由上方 `docs/` 规则等价承接，未新增/误伤任何排除 |
| `.gitignore` | 无需改 | — | 原文件无 `deliverables` 规则 | — | 无需处理 |
| 交叉引用-架构设计 | 改路径 | `docs/deliverables/compare_refactor_architecture_design.md:3` | `deliverables/compare_refactor_incremental_prd.md` → `docs/deliverables/compare_refactor_incremental_prd.md` | P1 | 低风险；同目录内互引路径更正 |
| 交叉引用-对齐安全报告 | 改路径 | `docs/deliverables/software-company/xbox-prod-align-safe-2026-07-20.md:77` | `deliverables/software-company/xbox-prod-align-2026-07-20.md` → `docs/deliverables/software-company/xbox-prod-align-2026-07-20.md` | P1 | 低风险 |
| 交叉引用-清理报告 | 改路径 | `docs/deliverables/software-company/xbox-cleanup-ai_llama-2026-07-20.md:57` | `deliverables/software-company/xbox-prod-align-2026-07-20.md:56,63` → `docs/deliverables/software-company/xbox-prod-align-2026-07-20.md:56,63` | P1 | 低风险 |
| 交叉引用-清理 QA 报告 | 改路径 | `docs/deliverables/software-company/xbox-cleanup-ai_llama-qa-2026-07-20.md:25,108,109,169` | 4 处 `deliverables/software-company/` → `docs/deliverables/software-company/` | P1 | 低风险；批量替换，均为散文/指针引用 |
| 交叉引用-方案A QA 报告 | 改路径 | `docs/deliverables/software-company/xbox-prod-align-a-qa-2026-07-20.md:136` | `deliverables/software-company/*.md` → `docs/deliverables/software-company/*.md` | P1 | 低风险；同文件 `:192` 的 `git status` 引用按历史记录保留（见第五节） |
| 交叉引用-方案A 报告 | 改路径 | `docs/deliverables/software-company/xbox-prod-align-a-2026-07-20.md:107` | 散文指称 `deliverables/` → `docs/deliverables/` | P1 | 低风险 |
| 交叉引用-归档 README | 改说明 | `docs/archive/2026-07-code-audit/README.md:4,5,6` | 第 4 行原路径更正为 `docs/deliverables/software-company/code-audit/`；第 5 行历史叙事保留；新增第 6 行「二次迁移说明（2026-07-20）：deliverables/ → docs/deliverables/」 | P1 | 低风险 |

## 三、迁移前后目录树对比

**迁移前（根级）**
```
X-box/
├── deliverables/
│   ├── compare_refactor_architecture_design.md
│   ├── compare_refactor_class-diagram.mermaid
│   ├── compare_refactor_incremental_prd.md
│   ├── compare_refactor_sequence-diagram.mermaid
│   └── software-company/
│       ├── xbox-delivery-2026-07-16.md
│       ├── xbox-cleanup-ai_llama-2026-07-20.md
│       ├── xbox-cleanup-ai_llama-qa-2026-07-20.md
│       ├── xbox-prod-align-2026-07-20.md
│       ├── xbox-prod-align-a-2026-07-20.md
│       ├── xbox-prod-align-a-qa-2026-07-20.md
│       ├── xbox-prod-align-qa-2026-07-20.md
│       ├── xbox-prod-align-safe-2026-07-20.md
│       └── xbox-prod-align-safe-qa-2026-07-20.md
└── docs/
    └── （原 docs/ 内容，不含 deliverables）
```

**迁移后（根级 `deliverables/` 消失，`docs/deliverables/` 出现）**
```
X-box/
└── docs/
    ├── deliverables/                ← 新增（原根级 deliverables/ 整体迁入）
    │   ├── compare_refactor_architecture_design.md
    │   ├── compare_refactor_class-diagram.mermaid
    │   ├── compare_refactor_incremental_prd.md
    │   ├── compare_refactor_sequence-diagram.mermaid
    │   └── software-company/
    │       ├── xbox-delivery-2026-07-16.md
    │       ├── xbox-cleanup-ai_llama-2026-07-20.md
    │       ├── xbox-cleanup-ai_llama-qa-2026-07-20.md
    │       ├── xbox-prod-align-2026-07-20.md
    │       ├── xbox-prod-align-a-2026-07-20.md
    │       ├── xbox-prod-align-a-qa-2026-07-20.md
    │       ├── xbox-prod-align-qa-2026-07-20.md
    │       ├── xbox-prod-align-safe-2026-07-20.md
    │       └── xbox-prod-align-safe-qa-2026-07-20.md
    ├── archive/2026-07-code-audit/README.md   ← 引用已更正 + 二次迁移说明
    └── （其余 docs/ 内容不变）
```

## 四、.gitignore / .dockerignore 改动要点

- **`.gitignore`**：**无需改**。原文件全程无 `deliverables` 字样，迁移不触及任何忽略规则。
- **`.dockerignore`**：原第 9 行 `deliverables/` 为排除 docker 构建上下文之用。因同文件第 8 行已有 `docs/` 规则（会一并排除 `docs/deliverables/`），为避免重复排除并消除过期字面量，将该行改为说明性注释，未新增任何排除项，等价保持「deliverables 内容不进 docker 构建上下文」的原意图。

## 五、内部交叉引用更新清单

| 源报告 | 改动处 | 原字符串 → 新字符串 |
|---|---|---|
| `compare_refactor_architecture_design.md` | 第 3 行 | `deliverables/compare_refactor_incremental_prd.md` → `docs/deliverables/compare_refactor_incremental_prd.md` |
| `xbox-prod-align-safe-2026-07-20.md` | 第 77 行 | `deliverables/software-company/xbox-prod-align-2026-07-20.md` → `docs/deliverables/software-company/xbox-prod-align-2026-07-20.md` |
| `xbox-cleanup-ai_llama-2026-07-20.md` | 第 57 行 | `deliverables/software-company/xbox-prod-align-2026-07-20.md:56,63` → `docs/deliverables/software-company/xbox-prod-align-2026-07-20.md:56,63` |
| `xbox-cleanup-ai_llama-qa-2026-07-20.md` | 第 25、108、109、169 行 | `deliverables/software-company/` → `docs/deliverables/software-company/`（4 处） |
| `xbox-prod-align-a-qa-2026-07-20.md` | 第 136 行 | `deliverables/software-company/*.md` → `docs/deliverables/software-company/*.md` |
| `xbox-prod-align-a-2026-07-20.md` | 第 107 行 | 散文指称 `deliverables/` → `docs/deliverables/` |
| `docs/archive/2026-07-code-audit/README.md` | 第 4、5、6 行 | 原路径更正为 `docs/deliverables/...`；新增第 6 行 2026-07-20 二次迁移说明 |

> 注：所有改动的均为路径字符串，未改变任何报告正文语义与结论。

## 六、全仓 deliverables 残留引用排查结论

全仓 Grep `deliverables` 复核结果：除下列**合理保留项**外，无任何指向根级 `deliverables/` 的有效（可导航）引用：

1. **`xbox-prod-align-qa-2026-07-20.md:155`** — `?? deliverables/software-company/xbox-prod-align-2026-07-20.md   # 报告`。
   属该 QA 报告内引用的**历史 `git status` 原始输出**，为事实记录而非导航链接；按「不篡改历史输出」原则保留。
2. **`xbox-prod-align-a-qa-2026-07-20.md:192`** — `?? deliverables/software-company/*.md (5 份)`。
   同上，为历史 `git status` 输出引用，保留。
3. **`docs/archive/2026-07-code-audit/README.md:5`** — 历史叙事「从 `deliverables/`（交付物暂存区）迁移至 `docs/archive/`」，描述 code-audit 当初的首次迁移，属刻意保留的历史说明。
4. **`docs/archive/2026-07-code-audit/README.md:6`** — 新增的「二次迁移说明（2026-07-20）：原 `deliverables/` 整体迁至 `docs/deliverables/`」，为本次迁移的历史记录，刻意保留旧路径名称。

> 其余所有 `deliverables` 命中均已变为 `docs/deliverables/...` 形式，指向新位置，链接有效。

## 七、契约与运行影响声明（重点）

- **凭证契约无需变更**：未读取、未修改任何真实密码/密钥/令牌（`.env.dev` / `.env.prod` 真实值未动；`.env.dev.example` 仅有的一条 `MYSQL_ROOT_PWD` 模板占位值亦未触碰）。
- **dev 运行行为不变**：未改动任何 dev 业务运行配置——`docker-compose.*.yml`、`Dockerfile*`、`db/`、`gateway/`、`scripts/`、volumes、平台配置等原样保留；本次仅移动文档目录并更新文档内路径字符串。
- **合并 docker compose config 不受影响**：主理人此前已 Grep 验证 compose / Dockerfile / yaml / sh 运行时文件均不引用 `deliverables`；本次迁移不涉及运行时文件，`docker compose config` 合并结果不受影响。`.dockerignore` 对 `docs/deliverables/` 的排除原本就由 `docs/` 规则覆盖，构建上下文行为不变。

---

**改动文件清单（含本次新增报告本身）**
- 目录迁移（git 重命名/新增）：`docs/deliverables/`（13 个文件，自 `deliverables/` 迁入）
- 修改：`.dockerignore`
- 修改：`docs/deliverables/compare_refactor_architecture_design.md`
- 修改：`docs/deliverables/software-company/xbox-prod-align-safe-2026-07-20.md`
- 修改：`docs/deliverables/software-company/xbox-cleanup-ai_llama-2026-07-20.md`
- 修改：`docs/deliverables/software-company/xbox-cleanup-ai_llama-qa-2026-07-20.md`
- 修改：`docs/deliverables/software-company/xbox-prod-align-a-qa-2026-07-20.md`
- 修改：`docs/deliverables/software-company/xbox-prod-align-a-2026-07-20.md`
- 修改：`docs/archive/2026-07-code-audit/README.md`
- **新增**：`docs/deliverables/software-company/xbox-move-deliverables-2026-07-20.md`（本报告）
