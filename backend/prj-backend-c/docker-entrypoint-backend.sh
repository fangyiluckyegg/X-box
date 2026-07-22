#!/bin/sh
# prod 后端启动包装脚本
# 问题：compose 把宿主机 ./logs/prj-backend-c 以 bind mount 覆盖到 /app/logs，
#       覆盖后目录属主为 root，而 Spring Boot 以非 root 的 appuser 运行，
#       logback 写文件会 Permission denied（dev 因 mvn 跑 root 无此问题）。
# 修复：以 root 启动时先把 /app/logs 重新 chown 给 appuser，再用 su 降权运行 Spring Boot。
set -e

mkdir -p /app/logs
chown -R appuser:appuser /app/logs

# 降权到 appuser 运行（root 切换非 root 无需密码）
exec su appuser -s /bin/sh -c 'exec java -Xmx1800M -jar /app/app.jar'
