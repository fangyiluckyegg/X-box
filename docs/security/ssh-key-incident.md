> 本文档由原 ai_llama/README-SECURITY.md 于 2026-07-20 归档（Ollama 已迁宿主机部署，ai_llama 容器目录废弃移除）。

# SSH 密钥安全说明

## 当前状态

`id_ed25519` 和 `id_ed25519.pub` 已从 Git 跟踪中移除（`.gitignore` 已配置屏蔽）。

文件仍保留在本地磁盘 `ai_llama/` 目录下供开发环境使用，但不会再被提交到代码仓库。

## 安全要求

1. **生产环境**：私钥不得存在于项目目录中，必须通过 CI/CD Secret 或运维配置管理工具（如 Ansible Vault、HashiCorp Vault）注入。
2. **密钥轮换**：由于私钥曾进入 Git 历史，存在泄露风险，**运维侧必须重新生成密钥对并轮换所有使用该密钥的服务**。
3. **历史清理**：如有条件，应使用 `git filter-repo` 或 BFG Repo-Cleaner 清理 Git 历史中的私钥文件。

## 操作步骤（运维侧）

```bash
# 1. 生成新密钥对
ssh-keygen -t ed25519 -f /secure/path/id_ed25519 -N "your_passphrase"

# 2. 将公钥部署到目标服务器
ssh-copy-id -i /secure/path/id_ed25519.pub user@target-server

# 3. 通过 CI/CD Secret 或环境变量注入私钥（不放入项目目录）
# 例如在 Docker 中通过 secret mount：
#   docker run -v /secure/keys:/keys:ro ...
```
