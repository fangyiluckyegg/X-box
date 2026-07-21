# 宿主原生 Ollama 部署说明（X-box）

> 适用：X-box 的 AI 向量服务（Ollama + bge-m3）改为**宿主原生部署**，
> 不再作为 Docker 容器运行。后端 `prj-backend-c` 通过
> `AI_SERVICE_URL=http://host.docker.internal:11434` 经宿主机网关访问本机 Ollama。
>
> 迁移日期：2026-07-16（详见 `docs/architecture/ollama-host-migration-design.md`）。

---

## 1. 为什么这样改

- 原方案把 Ollama 跑在容器里（`dev-prj-llama`），在 macOS / WSL2 上通常只能走 CPU，且编排复杂。
- 宿主原生部署后：Ollama 直接装在宿主机，后端容器经 `host.docker.internal` 访问。
- `host.docker.internal` 由 **Docker Desktop（Mac/Win）** 与 **OrbStack** 内置解析，**无需** `extra_hosts`。

---

## 2. 关键约束（务必遵守）

| 项 | 值 | 说明 |
|----|----|------|
| 宿主 Ollama 监听 | `OLLAMA_HOST=0.0.0.0:11434` | **必须 0.0.0.0**，否则容器经网关 IP 连不上（127.0.0.1 不行） |
| 模型 | `bge-m3:latest` | `ollama pull bge-m3`；须与后端 `AI_EMBED_MODEL`（缺省 `bge-m3:latest`）一致 |
| 后端地址 | `AI_SERVICE_URL=http://host.docker.internal:11434` | dev 与 prod **同值** |
| 端口 | `11434` | 固定 |

> ⚠️ **安全提示**：`0.0.0.0:11434` 会暴露到宿主机所有网卡（含 LAN），且 Ollama 默认无鉴权。
> 生产机务必依赖宿主防火墙 / 网络隔离（仅放行 docker 桥网 / localhost）；或后续用反向代理强制 `AI_API_TOKEN`（当前未启用）。

---

## 3. 一键初始化脚本

| 平台 | 脚本 | 说明 |
|------|------|------|
| macOS | `bash scripts/setup-host-ollama.sh` | Homebrew 安装 + 持久化 `OLLAMA_HOST` + 启动 + 拉模型 + 校验 |
| Windows | `pwsh scripts/setup-host-ollama.ps1` | winget/choco 安装 + 写用户环境变量 + 启动 + 拉模型 + 校验 |

可选参数：

```bash
bash scripts/setup-host-ollama.sh --pull-only   # 仅拉取/校验模型（已装好 Ollama 时）
pwsh scripts/setup-host-ollama.ps1 -PullOnly
```

---

## 4. 手动步骤（Mac）

```bash
# 1) 安装
brew install ollama

# 2) 持久化监听地址（让 Ollama 监听 0.0.0.0）
#    - 若用 brew services 托管，在 ~/Library/LaunchAgents/homebrew.mxcl.ollama.plist
#      的 <EnvironmentVariables> 增加 <key>OLLAMA_HOST</key><string>0.0.0.0:11434</string>
#    - 或直接：launchctl setenv OLLAMA_HOST 0.0.0.0:11434
export OLLAMA_HOST=0.0.0.0:11434

# 3) 启动
brew services start ollama        # 或：ollama serve &

# 4) 拉取模型
ollama pull bge-m3

# 5) 校验
curl -sf http://localhost:11434/api/tags | grep bge-m3
```

## 5. 手动步骤（Windows / WSL2 + Docker Desktop）

```powershell
# 1) 安装（二选一）
winget install --exact --id Ollama.Ollama -e
# choco install ollama -y

# 2) 持久化监听地址（用户环境变量）
[System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', '0.0.0.0:11434', 'User')
$env:OLLAMA_HOST = '0.0.0.0:11434'

# 3) 启动
Start-Process ollama -ArgumentList serve -WindowStyle Hidden

# 4) 拉取模型
ollama pull bge-m3

# 5) 校验
(Invoke-RestMethod http://localhost:11434/api/tags).models | Select-Object name
```

---

## 6. 启动顺序（强约束）

1. **宿主 Ollama 必须先于任何 `docker compose` 启动并就绪**（见上）。
2. 再起基础设施与后端：
   - dev：`docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d`
   - prod：`docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build`
3. 后端 `depends_on` **不再包含 Ollama**（Ollama 不在 compose 编排内）。

---

## 7. 排错

| 现象 | 原因 | 处理 |
|------|------|------|
| 后端报 `Ollama 异常，状态码：...` / 连接拒绝 | 宿主 Ollama 未起或模型未拉 | 先 `ollama serve` + `ollama pull bge-m3`，再重启后端 |
| 容器侧连不上 11434 | `OLLAMA_HOST` 只绑了 127.0.0.1 | 改为 `0.0.0.0:11434` 并重启 Ollama |
| `host.docker.internal` 无法解析 | 原生 Linux docker engine（非桌面版） | 在后端服务加 `extra_hosts: ["host.docker.internal:host-gateway"]`（目标机为 OrbStack/Docker Desktop，默认不需要） |
| 模型名不匹配 | 宿主拉的是 `bge-m3:latest`，后端 `AI_EMBED_MODEL` 不同 | 对齐两者模型名 |

---

## 8. 回退到容器 Ollama（紧急）

如需临时恢复容器版 Ollama：

1. 取消 `docker-compose.base.yml` 末尾**注释版回退块**（恢复 `dev-prj-llama` 服务）。
2. 将对应 compose / `.env` 的 `AI_SERVICE_URL` 改回 `http://dev-prj-llama:11434`。
3. 重新 `docker compose up -d`。

> 不引入 profile，保持编排简单（详见迁移设计 §7）。
