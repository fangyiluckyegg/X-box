# X-box 生产环境 Mac 部署手册（prod-mac-runbook）

> 适用场景：在 **Mac Mini M2 / macOS Sonoma / OrbStack 2.1.3** 上，以 **prod profile** 启动 X-box 生产栈，
> 复用 `docker-compose.base.yml` 的 `dev-mysql`；Ollama 改为**宿主原生部署**（后端经 `host.docker.internal:11434` 访问）。
> 本次改动属于 **"安全去密（O1）"** 配置级修复，**不涉及任何源码 / Dockerfile / 网关 / 前端改动**。

---

## 1. 前置条件

1. 已安装 **OrbStack 2.1.3**（或兼容的 Docker 运行时），`docker compose` 可用。
2. 已准备生产环境文件：
   - `cp .env.prod.example .env.prod`，并填入**强随机值**（切勿提交，`.env*` 已被 `.gitignore` 忽略）。
   - `cp .env.prod.backend.example .env.prod.backend`，并填入**强随机值**（切勿提交，`.env*` 已被 `.gitignore` 忽略）。
3. 已准备 Mac 本机的 **dev 环境文件**（从示例复制）：
   - `cp .env.dev.example .env.dev`
   - `cp .env.backend.example .env.backend`
   - **关键**：`.env.dev` 中的 `SPRING_DATASOURCE_PASSWORD` 必须与 `.env.prod.backend` 中的
     `SPRING_DATASOURCE_PASSWORD` **完全相同**（详见第 2 节"凭证契约"）。
   - `.env.dev` 必须 **UTF-8（LF）**；避免 CRLF 导致 wrapper 注入 `prj_user\r` 而被拒
     （wrapper 已做 `\r` 剥除兜底，仍建议源文件即干净）。

---

## 2. 凭证契约（核心，务必一致）

- 共享的 `dev-mysql` 容器中，`prj_user` 的口令**不由 `init.sql` 创建**（O1 已移除）。
- `prj_user` 在 MySQL 每次启动时，由
  `db/mysql_scripts/docker-entrypoint-wrapper.sh` 的 `ensure_app_user()`
  按 **MySQL 容器 env（base.yml 的 `.env.dev`）** 的 `SPRING_DATASOURCE_PASSWORD` 注入。
- 因此：
  - prod 后端用 `.env.prod.backend` 的 `SPRING_DATASOURCE_PASSWORD` 连接 `dev-mysql` 的 `prj_user`；
  - 该 `prj_user` 的口令 = `.env.dev` 的 `SPRING_DATASOURCE_PASSWORD`；
  - **两者必须相等**，否则 prod 后端连不上库。
- 同时 `.env.prod` 内的 `PRJ_DB_PWD` 与 `.env.prod.backend` 的 `SPRING_DATASOURCE_PASSWORD` 也使用同一值。

---

## 3. 启动命令

```bash
cd X-box
docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

> 说明：Ollama 已迁移为宿主原生部署（见第 6 节），不再作为容器运行，
> 故原「容器 llama 的 mem_limit」相关说明已不适用；宿主侧 Ollama 资源占用由宿主自行管理。

---

## 4. 验证

- 后端日志**无** `Host '...' is not allowed to connect` 或
  `Access denied for user 'prj_user'@'...'`。
- prod profile 严格校验通过后，后端启动日志应出现
  `[Security] 关键凭证校验通过`
  （校验项：JWT_SECRET ≥ 256-bit、DB/Redis/Druid 口令非弱默认值）。
- 网关 `/swagger-ui.html` 在 prod 下应被关闭（`SWAGGER_ENABLED=false`）。

---

## 5. 已知手动步骤（运维按现有 runbook 执行）

- `db/class_init/msg.sql`、`work.sql` **不在** `initdb.d` 下，不会自动执行，
  且未创建 / 授权 `class_user`；
- `prj-php` 连接 `dev-mysql` 需要 `class_user` 与 `msg` / `work` 库，
  需运维按现有 runbook **手动建立**；
- 执行依据：`docker-compose.prod.yml` 第 31-32 行引用的
  `docs/X-box-db-merge-runbook-2026-07-14.md`。

---

## 6. Ollama（宿主原生部署）

> 自 2026-07-16 起，Ollama + bge-m3 已改为**宿主原生部署**（不再作为容器运行）。
> 后端 `prj-backend-c` 通过 `AI_SERVICE_URL=http://host.docker.internal:11434` 经网关访问宿主 Ollama。

### 启动顺序（强约束）
1. **宿主 Ollama 必须先于任何 `docker compose` 启动并就绪**：
   - `ollama serve &`（或 `brew services start ollama`）；
   - `ollama pull bge-m3`（首次拉取权重）；
   - 校验：`curl -sf http://localhost:11434/api/tags | grep bge-m3`。
2. **再起基础设施与后端**：
   `docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build`。
   后端 `depends_on` 仅保留 mysql / prj-redis，**不再等待 Ollama**（Ollama 在宿主、不在 compose 编排内）。

### 为什么 `OLLAMA_HOST=0.0.0.0:11434`
- 后端容器经宿主**网关 IP**（非 127.0.0.1）访问 `host.docker.internal`，故宿主 Ollama 必须监听 `0.0.0.0`（而非仅 localhost），否则容器侧连接被拒。
- 一键初始化见 `scripts/setup-host-ollama.sh`（Mac）/ `scripts/setup-host-ollama.ps1`（Win）。

### 安全提示
- 宿主 Ollama `0.0.0.0:11434` 会暴露到宿主机所有网卡（含 LAN），且 Ollama 默认无鉴权。
- 生产机务必依赖宿主防火墙 / 网络隔离（仅放行 docker 桥网 / localhost）；或后续用反向代理强制 `AI_API_TOKEN`（当前未启用）。

### 失败模式
- 若宿主 Ollama 未起，首个 `/api/embed` 调用会报 `Ollama 异常，状态码：...` / 连接拒绝，由后端明确抛出（非静默退化）。

---

## 7. GBK / 编码提示

- `.env.prod` **必须 UTF-8（无 BOM）且行尾 LF**。若从 Windows 拷贝出现中文乱码（GBK），请转码：

```bash
file .env.prod                 # 确认无 "ISO-8859" / "GBK"
iconv -f GBK -t UTF-8 .env.prod > .env.prod.utf8 && mv .env.prod.utf8 .env.prod
# 确保行尾为 LF（非 CRLF）
sed -i '' 's/\r$//' .env.prod
```

---

## 8. 禁止改动（保持 dev 可用）

- 不要改 `.env.dev`、`.env.backend`、`docker-compose.base.yml`、
  `docker-compose.business-prj.dev.yml` 等 dev 文件——dev 已跑通。
- 不要改 `.env.prod.backend`（生产后端专用）。
- 不要改应用源码、Dockerfile、网关 conf、前端代码。
- 不要把任何明文口令写入会被 git 跟踪的文件。
