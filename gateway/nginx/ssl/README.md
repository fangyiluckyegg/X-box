# Nginx TLS 证书目录（C6）

本目录用于放置 PRJ 网关的 TLS 证书。

- `prj.crt`：服务器证书
- `prj.key`：私钥

当前为**无证书**状态，网关以 HTTP(80) 对内网提供服务（见 `conf.d/prj.conf` 顶部"仅限内网部署"声明）。

## 启用 HTTPS（公网暴露前必须）

1. 将证书放到本目录：`prj.crt`、`prj.key`
2. 编辑 `conf.d/prj.conf`，取消"预留 TLS server 块（443）"注释
3. 取消 HSTS `add_header` 注释
4. 将 80 端口 server 块改为 301 跳转到 HTTPS
5. 重新加载 Nginx：`nginx -s reload`

> ⚠️ 证书文件含敏感私钥，请勿提交进仓库（已被 `.gitignore` 忽略）。
