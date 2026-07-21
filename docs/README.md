# X-box 项目文档导航（Docs Index）

本页是 X-box 容器化项目的**统一文档入口**。按主题归类各文档，并附本地访问端口速查与已知问题。

## 一、文档地图（按主题）

| 主题 | 文档入口 | 内容 |
|------|----------|------|
| 项目总纲 | `../项目开发说明` | 根目录主文档：项目整体说明、启动与约定（不在 docs/ 下） |
| 架构·总览 | `architecture/architecture_review.md` | 架构评审与现状/目标拓扑（配 `architecture_current_topology.mmd` / `architecture_target_topology.mmd`） |
| 架构·升级 | `architecture/upgrade-boot3-assessment.md` | Spring Boot 3 升级评估 |
| 架构·Ollama | `architecture/ollama-host-migration-design.md` | AI 推理迁宿主原生 Ollama 设计（配时序/拓扑图） |
| 架构·后端验证 | `architecture/prj-backend-c-boot3-e2e-verification-design.md` | prj-backend-c Boot3 E2E 验证设计 |
| 部署·运行手册 | `deployment/prod-mac-runbook.md` | 生产 Mac mini 运行手册（启栈、凭证契约、排错） |
| 部署·指南 | `deployment/Mac-mini-deployment-guide.md` | Mac mini 部署详细指南 |
| 安全·口令清理 | `security/prod-secret-history-cleanup.md` | 生产口令历史清理（Redis/DB 明文泄漏修复那轮） |
| 安全·事件 | `security/ssh-key-incident.md` | SSH 密钥安全事故记录 |
| 验证·QA | `verification/qa-verification.md` | QA 验证清单（含 curl 探测各入口用例） |
| 验证·评审 | `verification/code-review-report.md` | 代码评审报告 |
| 审计归档（索引） | `audit/README.md` | 历次安全/清理/代码审计的归档索引（只读历史） |

> 所有归档材料在 `audit/archive/` 下，按日期线分置，仅供追溯查阅。

## 二、本地访问端口速查

所有对外端口均绑定 `127.0.0.1`（仅本机可访问）；统一入口是 nginx 网关（80/443）。

| 入口用途 | 地址 | 说明 |
|----------|------|------|
| 前端主入口（推荐） | http://localhost/ | 经 nginx:80 反代到 `prj-frontend:8081` |
| 前端 HTTPS | https://localhost:443/ | 自签证书（gateway/nginx/ssl/prj.crt），浏览器会报不安全需手动信任 |
| 前端容器直连 | http://localhost:8081/ | dev 暴露所有网卡，prod 仅 127.0.0.1 |
| 后端 API（经网关） | http://localhost/api/... | 经 nginx:80 反代到 `prj-backend-c:8080` |
| 后端直连 | http://localhost:8080/ | 仅 dev 暴露；含 /login、/captchaImage、/druid、/swagger-ui、/doc.html |
| 班级网站 Niu_Txl（607） | http://localhost/607/ | 经网关转发到 `prj-php:80`（Apache docroot 子目录 607） |
| 班级网站 Niu_Txl（902） | http://localhost/902/ | 同上，902/message 连 msg 库、902/work 连 work 库 |
| 班级网站直连（兜底） | http://localhost:1181/607/ 、 http://localhost:1181/902/ | PHP 容器直连；frp 穿透时由 VPS frps 终止 TLS |
| MySQL（管理工具） | 127.0.0.1:33060 | 映射容器内 3306，用 prj_user 账号 |
| Redis（管理工具） | 127.0.0.1:63790 | 仅 dev 暴露，需 REDIS_PASSWORD 认证 |

nginx 路由规则见 `gateway/nginx/conf.d/prj.conf`：`/` → 前端；`/api/`、`/login`、`/employee_kpi`、`/compare`、`/druid`、`/swagger-ui` 等 → 后端；`/607/`、`/902/` → 班级网站 PHP。

## 三、Niu_Txl（607/902 班级网站）说明

- 技术栈：Apache + PHP 8.2 + mysqli + gd，docroot 为 `Niu_Txl/`（挂载到容器 `/var/www/html`）。
- 607、902 为 `Niu_Txl/` 下的两个子站点目录，分别提供班级网站的不同功能模块。
- 数据库约定：902/message 回退连 `msg` 库，902/work 回退连 `work` 库；账号为 `class_user`（由 base 的 dev-mysql 在建库期授权）。
- 访问方式：优先经网关 `http://localhost/607/`、`http://localhost/902/`；若网关未启用，可直接访问 PHP 容器 `http://localhost:1181/607/`、`http://localhost:1181/902/`。
- 外网暴露：经 frp 内网穿透到自有 VPS（端口 1181），TLS 在 VPS 的 frps 侧终止；frpc/frps 配置与公网域名不在此仓库内。

## 四、启栈命令

开发：
```
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d --build
```
生产：
```
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

## 五、已知缺口

- 仓库内无 PaaS（Vercel/Railway/Cloud Studio 等）部署配置；外网访问依赖用户自有 VPS + frp，域名不在仓库。
- 当前文档无 Niu_Txl 的独立设计文档，运行约定见本页第三节与 compose 注释。
