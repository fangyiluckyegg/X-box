# X-box · Niu_Txl 根目录文件安全化修改 — 交付报告

- 日期：2026-07-20
- 项目：X-box 容器化（dev: Windows/Intel，prod: macOS M2 ARM64/OrbStack）
- 任务类型：文件安全化修改（快速模式）
- 工作流：TeamCreate → 工程师(修改+删除+文档) → QA(回归验证)
- 团队：`software-niutxl-rootfiles`

## TL;DR
按用户确认方案，修正了 Niu_Txl 根目录三个文件：Dockerfile.classphp 注释更正（7.4→8.2）、index.php 去 phpinfo 改 301 重定向、删除临时文件 index.phpecho，并同步更新开发说明标注；QA 回归验证 docker compose 配置零回归。

## 交付概览
| 项 | 结果 |
|---|---|
| 交付状态 | ✅ 完成 |
| 测试通过率 | 100%（QA 全部检查 PASS） |
| 智能路由判定 | NoOne（全部通过，无源码/测试 Bug） |
| 已知问题数 | 0（修改无回归） |

## 执行步骤与产物

### 1. Dockerfile.classphp 注释更正（工程师）
- 第 2 行：`# 607/902 班级网站运行环境：Apache + PHP 7.4 + mysqli + gd`
  → `# 607/902 班级网站运行环境：Apache + PHP 8.2 + mysqli + gd`
- 仅改注释，第 5 行 `FROM php:8.2-apache` 及所有 RUN/HEALTHCHECK 未动；编码 LF 保持不变。

### 2. index.php 安全化改写（工程师）
- 原内容：`<?php phpinfo(); ?> <?php phpinfo(); ?>`（暴露完整环境信息）
- 新内容（UTF-8 + CRLF 保留）：
  ```php
  <?php
  // Niu_Txl 根入口：重定向到实际班级站，避免暴露 phpinfo()
  header('Location: /902/message/index.php', true, 301);
  exit;
  ```
- 访问 `http://localhost:1181/` 将 301 跳转至 `/902/message/index.php`，不再泄露 phpinfo。

### 3. 删除 index.phpecho（工程师）
- `rm -f Niu_Txl/index.phpecho`，临时调试文件，已被 .gitignore/.dockerignore 排除，无业务引用。

### 4. 项目开发说明 标注更新（工程师）
| 文件 | 原标注 | 新标注 |
|---|---|---|
| .dockerignore | `# 是否可以整合？` | `# 保留：Docker build context 专属，不能并入根` |
| .gitignore | `# 是否可以整合？` | `# 保留：子目录 Git 忽略，可并入根但保留更清晰` |
| Dockerfile.classphp | `# 待核验` | `# PHP 8.2（注释已更正）` |
| index.php | `# 待核验` | `# 已安全化：去 phpinfo，重定向至 /902/message/index.php` |
| index.phpecho | `# 待核验`（整行） | 已删除 |

## 关于两个 ignore 文件为何不并入根目录（用户问询答复）
- **`Niu_Txl/.dockerignore` 必须保留在子目录**：Docker 的 `.dockerignore` 只对 build context 目录生效。prj-php 的 build context 是 `./Niu_Txl`（docker-compose.prod.yml:138 / classphp.dev.yml:8），Docker 只在 `./Niu_Txl` 找 `.dockerignore`；根级 `.dockerignore` 针对的是后端构建（context=仓库根），对 prj-php 无效。删除会导致 ~1.8GB 大媒体被传进镜像构建。
- **`Niu_Txl/.gitignore` 可并入根但保留更佳**：Git 支持任意层级匹配（加 `Niu_Txl/` 前缀即可合并），但子目录 `.gitignore` 是标准惯例，能局部化规则、降低根文件体积、避免 `git add` 时误带入大媒体。若需合并可单独再做一轮。

## QA 回归验证（严过关）
| 验证项 | 命令 | 结果 |
|---|---|---|
| docker compose 配置解析 | `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod config` | EXIT 0 ✅ |
| index.phpecho 删除确认 | `test -f Niu_Txl/index.phpecho` | 不存在 ✅ |
| Dockerfile 注释/版本 | `sed -n '2p;5p'` | 第2行 PHP 8.2 / 第5行 php:8.2-apache ✅ |
| index.php phpinfo 检查 | 内容核对 | 无可执行 phpinfo() 调用（仅注释提及字样）✅ |
| index.phpecho 全仓引用 | `grep -rn`（排除 .git） | 仅 2 处忽略规则，无业务引用 ✅ |

- 服务数 = 7（mysql / nginx-gateway / prj-backend-c / prj-frontend / prj-php / prj-redis / redis）
- 端口唯一性：80/443/33060/63790/8081/1181 各一次，无冲突
- `prj-redis` 无 host 端口映射；`prj-php` build context=./Niu_Txl、dockerfile=Dockerfile.classphp 未变
- 备注：compose 解析有 5 条 `aJs variable is not set` 告警，属 .env.prod 既有未定义变量，与本次改动无关

## 文件清单
| 动作 | 路径 |
|---|---|
| 修改 | `Niu_Txl/Dockerfile.classphp`（注释 7.4→8.2） |
| 修改 | `Niu_Txl/index.php`（去 phpinfo，改 301 重定向） |
| 删除 | `Niu_Txl/index.phpecho` |
| 修改 | `项目开发说明`（5 处标注更新） |

## 用户下一步建议
1. 验证重定向目标：`/902/message/index.php` 在 902 子站实际可访问；如需换主站入口（如 `/607/index.php`）告知我即可调整。
2. 遗留 `frpc.class-http.example.toml`（开发说明第 63 行仍标 `# 是否可以删除？`），如确认不再使用 frpc 穿透，下轮可清理。
3. `aJs` 变量告警：`docker-compose.prod.yml` 引用了 `.env.prod` 中未定义的 `aJs`，建议确认是否仍需或补定义。
4. 建议收敛工作区：累计多轮未提交改动，`git add -A && git commit` 一次性入库。
