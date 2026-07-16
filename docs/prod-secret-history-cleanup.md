# X-box 生产凭证历史清理手册（prod-secret-history-cleanup）

> 用途：彻底清除已 commit 进 git 历史的「当前生产」明文密钥。
> O1 已让仓库**工作树**零明文，但历史提交里仍可读到旧明文。本手册在 **Mac 侧**（有 git 权限的环境）执行，补齐这一环。

## 前置条件
- 在 git 仓库根 `X-box/` 执行。
- 已安装 `git-filter-repo`：`brew install git-filter-repo` 或 `pip install git-filter-repo`。
- 工作区干净、无未推送提交。
- 若仓库有协作者：先沟通——下面的 force-push 会改写所有历史哈希，协作者需删旧 clone 后重新 clone。

## 步骤

### 0. 备份（留后路）
```bash
cd X-box
cp -R . ../X-box-backup-$(date +%Y%m%d)
```

### 1. 安装 git-filter-repo
```bash
brew install git-filter-repo
# 或：pip install git-filter-repo
```

### 2. 生成替换清单（仅含 7 个「当前生产」密钥）
从本机 `.env.prod` 取值（`.env.prod` 已被 gitignore，安全，不会把明文再写进仓库）：
```bash
grep -E '^(PRJ_DB_PWD|SPRING_DATASOURCE_PASSWORD|REDIS_PASSWORD|JWT_SECRET|DRUID_PASSWORD|AI_API_TOKEN|CLASS_DB_PWD)=' .env.prod \
  | sed 's/^[^=]*=//' > /tmp/xbox-secrets.txt
```
若历史文档里还有 `.env.prod` 没有的值（例如 `MYSQL_ROOT_PASSWORD`），手动补一行**真实值**：
```bash
echo '那串真实值' >> /tmp/xbox-secrets.txt
```

> ⚠️ **刻意不进清单的项**：历史/弱默认值（`Prj@Dev789`、`QaTest@2026` 等）。
> 按团队决策**保留**——它们硬编码在已发布源码（`StartupSecurityValidator` 弱值黑名单、`docker-entrypoint-wrapper.sh` 的 `${...:-Prj@Dev789}` 默认回退、测试 fixture `WEAK_DB`）。仅抹文档会破坏校验器与测试，且无安全增益。O1 的「仓库零明文当前生产凭证」目标已达成，无需动它们。

### 3. 重写全部历史
```bash
git filter-repo --replace-text /tmp/xbox-secrets.txt
```
所有清单中的串会被替换为 `***REMOVED***`，覆盖每一个分支与标签的历史 blob。

### 4. 强制推送
```bash
git push --force --all
git push --force --tags
```
> 会改变所有分支/标签历史哈希。推送后通知所有协作者：删除旧 clone，重新 clone。

### 5. 清理临时扫描脚本
```bash
rm -f scan_secrets_tmp.py
git rm --cached scan_secrets_tmp.py 2>/dev/null
git status --short
```
> `scan_secrets_tmp.py` 是一次性密钥扫描器，提交前务必从工作树删除并确认未被 `git add`。

## 验证
```bash
# 历史里不应再出现这 7 个特征片段（.env.prod 被忽略，不计入）
git log -p --all | grep -E '<REDACTED-live-prod>|<REDACTED-live-prod>|<REDACTED-live-prod>|<REDACTED-live-prod>|<REDACTED-live-prod>|<REDACTED-live-prod>|<REDACTED-live-prod>' || echo "CLEAN"
```

## 关联事项
- **O1 凭证契约**：上线前确保本机 `.env.dev` 的 `SPRING_DATASOURCE_PASSWORD` 与 `.env.prod` **完全相同**（共享 `dev-mysql` 的 `prj_user` 口令须一致，否则 prod 后端连不上库）。
- **工作树已脱敏**：remediation ① 已把文档里的当前生产密钥替换为 `<REDACTED-live-prod>`；本手册只负责清历史 commit 版本。
- **启动顺序**：见 `docs/prod-mac-runbook.md`。
