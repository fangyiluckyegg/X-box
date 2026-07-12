@echo off
cd D:\crh123dexiaohao\X-box
echo 停止所有其他业务容器
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml down
echo 启动全部容器
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --force-recreate mysql
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build prj-frontend
:: docker compose -f docker-compose.business-prj.dev.yml restart prj-backend-c
:: docker ps

:: 全面清理后，重启全部容器
:: # 清理旧构建缓存、故障容器
:: docker builder prune -f
:: 清理旧容器镜像
:: docker-compose.base.yml down --rmi all -v

:: 重部署 runbook（在你 X-box 机器上）
:: 1. 停掉并删容器+卷（连带清掉旧的 Ollama 模型卷）
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml down -v
:: 2. 清镜像（验证 C16 从零构建，这步会触发重新联网拉 bge-m3）
:: docker image prune -a -f        # 或只 rmi 相关镜像
:: 3. 删本地代码 + 重新克隆+切到整改分支（关键！默认是 main 旧代码）
:: rm -rf /path/to/X-box
:: git clone <repo-url> X-box && cd X-box
:: http版本
:: git clone https://github.com/fangyiluckyegg/X-box.git X-box
:: cd X-box
:: git checkout feature/xbox-ollama-bake-and-dev-fixes
:: ssh版本
:: git clone git@github.com:fangyiluckyegg/X-box.git X-box
:: cd X-box
:: git checkout feature/xbox-ollama-bake-and-dev-fixes
:: 5. 重新构建+部署（构建机需联网）
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build

echo ==============================
echo 当前开发项目：Prj（全部容器已经重启）
echo ==============================
pause