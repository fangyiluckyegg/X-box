# X-box 生产部署指南（Apple Silicon Mac mini）

> 适用：Vue + Spring Boot 全栈（X-box），目标机 **Apple Silicon Mac mini（M1/M2/M3/M4，macOS）**，容器化生产部署。
> 配套产物：`docker-compose.prod.yml`（已做 Apple Silicon 适配）、`.env.prod`（密钥模板）、`gateway/nginx/conf.d/prj.conf`（已加固）、`docs/verification/code-review-report.md`（代码审查）、`docs/verification/qa-verification.md`（测试与验证手册）。

---

## 0. TL;DR

1. Mac mini 装 **Docker Desktop for Mac（Apple Silicon 版）**，分配 ≥4 vCPU / ≥8GB 给 Docker。
2. 填 `.env.prod` 强密钥（openssl 生成），并保证 `db/mysql_init/init.sql` 里 `prj_user` 密码与之一致。
3. `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build` 一把起。
4. **性能关键点**：Ollama 建议在 Mac 上**原生运行**（走 GPU），而非塞进 Docker（容器内无 Metal，仅 CPU，极慢）。
5. 上线后靠 **健康检查 + 日志 + 监控栈 + 备份** 守住稳定性。

---

## 1. 本次已修复 / 已适配的部署阻断项

| 项 | 问题 | 处理 |
|----|------|------|
| 前端端口 | 前端 nginx `listen 80`，网关代理 `prj-frontend:8081` → 首页 502 | `web/prj-frontend/nginx.conf` 改为 `listen 8081` ✅ |
| 后端服务名 | prod 后端服务名 `prj-backend`，网关代理 `prj-backend-c` → 全 API 502 | `docker-compose.prod.yml` 服务 key 改为 `prj-backend-c` ✅ |
| `.env.prod` 缺失 | prod compose 引用 `.env.prod` 但仓库无此文件，且 `application-prod.yml` 四凭证无默认 → 启动失败 | 已生成 `.env.prod` 模板（见第 3 节）✅ |
| Redis 镜像 | `redis:5.0.14-alpine` 已 EOL，官方 arm64 镜像缺失，Apple Silicon 拉取失败 | 升级 `redis:7-alpine` ✅ |
| 架构平台 | 未显式指定平台，可能触发 amd64 模拟退化 | 所有服务加 `platform: linux/arm64` ✅ |
| 资源限制 | prod 无内存/CPU 限制，Mac 小内存机型易被单容器吃满 | 各服务加 `mem_limit`/`cpus` ✅ |
| 健康检查 | mysql/redis 无健康检查，后端可能连未就绪的依赖 | 加健康检查 + 后端 `depends_on: service_healthy` ✅ |
| IP 伪造 | 网关 `X-Forwarded-For` 用 `$proxy_add_x_forwarded_for`（追加），客户端可伪造首 IP 绕过登录锁定 | 改为覆盖式 `$remote_addr` ✅ |

代码层面另修复 1 处内存泄漏（`CompareController.RESULT_CACHE` 只写不删，见审查报告 F-01）。

---

## 2. 环境准备（Mac mini）

1. **安装 Docker Desktop for Mac（Apple Silicon）**
   - 官网下载 `Docker Desktop for Mac with Apple silicon`；不要装 Intel 版。
   - 首次启动完成引导，确认 `docker version` 中 `Architecture: arm64` / `OS/Arch: linux/arm64`。
2. **分配资源**（Docker Desktop → Settings → Resources）
   - 建议：CPU ≥ 4、Memory ≥ 8GB（若 Mac 本身 8GB，见第 6 节把 Ollama 移出容器后总占用约 4GB，可运行；16GB 及以上更从容）。
   - 勾选 **Use Rosetta for x86/amd64 emulation**（兜底，本方案已统一 arm64 镜像，多数情况用不到）。
   - 确认 **Virtualization framework** 开启。
3. **文件共享**：把项目目录（`X-box/`）加入 Docker Desktop → Settings → Resources → File Sharing，避免卷挂载权限问题。
4. **网络**：生产仅 `127.0.0.1` 暴露（compose 已绑定回环）；如需从其他机器访问，走反向隧道 / 云 LB / VPN，并强制 TLS（第 8 节）。

---

## 3. 密钥与 `.env.prod`

`.env.prod` 已随本指南生成（模板，含占位符）。在 Mac 上：

```bash
cd /path/to/X-box

# 1) 复制并填写强密钥
cp .env.prod .env.prod.local
# 用 openssl 生成并替换下列值（不要提交真实密钥到 Git）：
JWT_SECRET=$(openssl rand -base64 32)
SPRING_DATASOURCE_PASSWORD=$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 24)
REDIS_PASSWORD=$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 24)
DRUID_PASSWORD=$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 24)
MYSQL_ROOT_PASSWORD=$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 24)
```

**数据库密码一致性（重要）**：`application-prod.yml` 的 `SPRING_DATASOURCE_PASSWORD` 必须与 MySQL 里 `prj_user` 账号密码一致，否则后端起不来。二选一：
- **全新部署**：把 `db/mysql_init/init.sql` 中 `CREATE USER IF NOT EXISTS 'prj_user'@'%' IDENTIFIED BY 'Prj@Dev789';` 的 `'Prj@Dev789'` 改成与 `SPRING_DATASOURCE_PASSWORD` 相同的强密码（init 仅在全新 volume 执行一次）；
- **已有库**：首次启动后执行 `ALTER USER 'prj_user'@'%' IDENTIFIED BY '<上方密码>'; FLUSH PRIVILEGES;`，再 `docker compose restart prj-backend-c`。

> 把 `.env.prod`、`.env.prod.local` 加入 `.gitignore`，切勿提交真实密钥。

---

## 4. 构建与启动

> 推荐使用统一部署脚本一条命令完成（含 Ollama 准备与凭证校验）：`bash scripts/deploy.sh --env prod`（等价于下方手动 compose 启动；可加 `--skip-ollama`、`--proxy <url>`）。

```bash
cd /path/to/X-box

# 后台构建并启动整栈（首次构建后端会跑 Maven，依赖阿里云镜像，较慢；建议留足时间）
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 查看启动进度与日志
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f prj-backend-c
```

注意：
- 后端构建用多阶段 Maven（`backend/prj-backend-c/Dockerfile.prod`），首次需联网拉取依赖（已配阿里云 `settings.xml`）。CI 环境建议开启镜像缓存。
- 后端会等 `prj-mysql`/`prj-redis` 健康检查通过后才启动（已配置 `depends_on: service_healthy`）。
- 构建上下文已通过 `.dockerignore` 精简（排除 web/ai_llama/docs 等），构建更快。

---

## 5. 冒烟验证

```bash
# 容器全部 healthy
docker ps --format '{{.Names}}\t{{.Status}}'

# 首页（经网关 :80 -> 前端 :8081）
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1/

# 验证码 / 登录入口（经网关 -> 后端 :8080）
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1/captchaImage

# 后端健康检查（Dockerfile.prod 中 wget /captchaImage）
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1/api/...   # 替换为真实业务接口

# 依赖连通性
docker compose -f docker-compose.prod.yml exec prj-backend-c wget -qO- http://localhost:8080/captchaImage
```

预期：首页 200、验证码 200、后端接口按鉴权返回 200/401。若 502，先核对 `gateway/nginx/conf.d/prj.conf` 的服务名/端口（已对齐 `prj-frontend:8081`、`prj-backend-c:8080`）。

---

## 6. Ollama 性能优化（重点）

**问题**：`prj-llama`（Ollama）跑在 Docker 内时，**无法访问 Mac 的 GPU（Metal）**，只能 CPU 推理，AI 比对会非常慢；且该容器默认吃 6GB 内存，8GB Mac 上易 OOM。

**推荐方案：Mac 原生运行 Ollama（用 GPU）**
```bash
# 1) 本机安装并启动 Ollama（自动用 Apple GPU 加速）
brew install ollama
ollama serve &            # 或 brew services start ollama
ollama pull llama3.2      # 及 bge-m3 等所需模型

# 2) 让后端改连本机 Ollama：在 .env.prod 增设
#    AI_SERVICE_URL=http://host.docker.internal:11434
#    （host.docker.internal 在 Docker Desktop for Mac 默认可达宿主机）
#    并修改 docker-compose.prod.yml 中 prj-backend-c.environment.AI_SERVICE_URL 同值

# 3) 注释/删除 prod compose 里的 prj-llama 服务（释放 6GB 容器内存）
```
> 原生 Ollama 与容器后端通过 `host.docker.internal:11434` 互通；模型权重存于 `~/Library/Application Support/Ollama`，持久且走 GPU。

**备选**：必须容器化 AI 时，保留 `prj-llama`，但把 `mem_limit` 调到机型允许值，并接受 CPU 速度。

---

## 7. 性能优化清单

- **JVM**：`Dockerfile.prod` 当前 `-Xmx1800M`。内存充裕可调大到 `-Xmx3g`；保持 G1GC（JDK17 默认）。监控 GC：挂载 `/app/logs` 已由 Dockerfile 处理。
- **MySQL**：已加 `--innodb-buffer-pool-size=512M`；内存大可上调到物理内存的 50%~70%。数据落 `./db/mysql_data`（SSD 优先）。
- **Redis**：已设 `maxmemory 384mb` + `allkeys-lru`，防雪崩/穿透把内存打满。
- **Docker 构建**：`.dockerignore` 已精简后端构建上下文；建议开启 BuildKit（Docker Desktop 默认开），CI 缓存 Maven 层。
- **Nginx**：`gateway/nginx/conf.d/prj.conf` 可加 `gzip on;`、按 CPU 核数设 `worker_processes auto;`（需改镜像或挂载自定义 nginx.conf）。
- **静态资源**：前端 dist 由 nginx 托管，`expires -1` 已禁用缓存，发版即生效。

---

## 8. 安全加固

1. **启用 TLS（强烈建议，尤其对外暴露时）**
   ```bash
   # 生成自签证书（生产建议用受信证书 / Let's Encrypt DNS 验证）
   openssl req -x509 -newkey rsa:2048 -nodes \
     -keyout gateway/nginx/ssl/prj.key -out gateway/nginx/ssl/prj.crt \
     -days 365 -subj "/CN=your-domain.example.com"
   ```
   取消 `gateway/nginx/conf.d/prj.conf` 末尾 443 server 块注释，设 `server_name`，并取消 `Strict-Transport-Security` 注释；`docker compose restart prj-nginx`。compose 已发布 `443`。
2. **JWT 令牌保护（代码层建议，F-11）**：当前 JWT 存非 HttpOnly Cookie，HTTP 明文可被嗅探。启用 TLS 后风险降低；后续建议 `auth.js`/`TokenService` 改为写 `HttpOnly; Secure; SameSite=Strict` Cookie。
3. **fastjson2 autoType（代码层建议，F-04）**：`RedisConfig` 反序列化开启 `SupportAutoType`，建议加类型白名单（`AutoTypeFilter` 仅允许 `LoginUser`），防止恶意 `@type` gadget。
4. **暴露面**：compose 所有端口均绑定 `127.0.0.1`；公网暴露务必经 TLS 反代 + 防火墙，禁止 0.0.0.0。
5. **依赖与镜像**：定期 `docker compose pull` 更新基础镜像，关注 CVE。

---

## 9. 监控、日志与告警

- **健康检查**：各服务 `docker ps` 显示 `healthy`；后端依赖 mysql/redis healthy 才起。可定时 `docker ps --filter health=unhealthy` 巡检。
- **日志**：统一挂载到 `./logs/{nginx,mysql,redis,llama,prj-backend-c,prj-frontend}/`。**加轮转**避免磁盘写满（Mac 上用 `newsyslog` 或定时脚本对 `./logs` 做 `logrotate` 等价处理）。
- **资源**：`docker stats` 实时查看；Docker Desktop 仪表盘可看历史。
- **推荐轻量监控栈**（可选，额外约 1GB 内存，低配机慎用）：新建 `docker-compose.monitor.yml`：
  ```yaml
  services:
    cadvisor:
      image: gcr.io/cadvisor/cadvisor:latest
      platform: linux/arm64
      volumes: [/:/rootfs:ro, /var/run:/var/run:ro, /sys:/sys:ro, /var/lib/docker:/var/lib/docker:ro]
      ports: ["127.0.0.1:8088:8080"]
    prometheus:
      image: prom/prometheus:latest
      platform: linux/arm64
      volumes: [./monitor/prometheus.yml:/etc/prometheus/prometheus.yml:ro]
      ports: ["127.0.0.1:9090:9090"]
    grafana:
      image: grafana/grafana:latest
      platform: linux/arm64
      ports: ["127.0.0.1:3000:3000"]
  ```
  再在 Grafana 加 cAdvisor/Prometheus 数据源，看 CPU/内存/容器存活。
- **外部存活监控**：用 Uptime Kuma / 云监控定时探 `http://127.0.0.1/` 与 `/captchaImage`，异常发邮件/Webhook。
- **告警**：容器退出或健康检查失败 → 可借 `docker events` + 脚本或监控栈告警。

---

## 10. 备份与回滚

- **数据库备份**（建议每日，crontab）：
  ```bash
  docker compose -f docker-compose.prod.yml exec -T prj-mysql \
    mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" prj_prod | gzip > backup/prj_prod_$(date +%F).sql.gz
  ```
- **回滚**：代码用 git 回退到稳定 tag 后重新 `up -d --build`；或构建时给镜像打版本 tag，回退时 `docker compose up -d` 指定旧 tag。
- **配置回滚**：`.env.prod`、`docker-compose.prod.yml`、`prj.conf` 均纳入 Git（密钥除外），可随代码版本回退。

---

## 11. 故障排查

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `redis` 镜像拉取失败 | 旧 `redis:5.0.14-alpine` 无 arm64 | 已升级 `redis:7-alpine`；`docker compose pull prj-redis` |
| AI 比对极慢 | Ollama 在容器内仅 CPU | 改 Mac 原生 Ollama（第 6 节） |
| 502 Bad Gateway | 服务名/端口错位 | 已修；仍异常则核对 `prj.conf` 与 compose 服务名/端口 |
| 后端一直 `starting` | mysql/redis 未 healthy | `docker logs prj-mysql`；确认 `.env.prod` 密码正确 |
| 后端启动即退出 | 缺密钥 fail-fast | 确认 `.env.prod` 含 `SPRING_DATASOURCE_PASSWORD/DRUID_PASSWORD/REDIS_PASSWORD/JWT_SECRET` |
| 磁盘写满 | 日志未轮转 | 加 logrotate/newsyslog（第 9 节） |

---

## 12. 本次部署相关文件清单

- `docker-compose.prod.yml`（重写：arm64 / redis7 / 资源限制 / 健康检查 / 依赖健康）
- `.env.prod`（新增：生产密钥模板）
- `gateway/nginx/conf.d/prj.conf`（X-Forwarded-For 覆盖式加固）
- `.dockerignore`（精简后端构建上下文）
- `web/prj-frontend/nginx.conf`（端口 8081，对齐网关）
- `docs/verification/code-review-report.md`（代码审查报告）
- `docs/verification/qa-verification.md`（QA 验证与测试手册，由 QA 工程师产出）
- `backend/prj-backend-c/src/.../controller/CompareController.java`（内存泄漏修复）

> 协作：代码审查/修复 = 工程师寇豆码；验证/测试 = QA 严过关；本部署方案 = 交付总监齐活林。
