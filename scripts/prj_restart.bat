@echo off
cd D:\crh123dexiaohao\X-box
echo 停止所有其他业务容器
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml down
:: docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod down -v
:: 第 1 步：清掉被反复失败部署搞脏的 mysql 卷（重建干净；prod 全新部署无碍）
:: docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod down -v
:: 第 2 步：重跑（脚本现已能自动定位 Git 的 openssl 补证书 + 跳过受限 Ollama）
:: powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 -Env prod -SkipOllama


echo 启动全部容器
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d --build

docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --force-recreate mysql
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build prj-frontend
:: docker compose -f docker-compose.business-prj.dev.yml restart prj-backend-c
:: docker ps

:: 重部署 runbook（在你 X-box 机器上）
:: 1. 停掉并删容器+卷（连带清掉旧的 Ollama 模型卷）
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml down -v
:: 2. 清镜像（验证 C16 从零构建，这步会触发重新联网拉 bge-m3）
:: # 清理旧构建缓存、故障容器
:: docker builder prune -f
:: 清理旧容器镜像
:: docker-compose.base.yml down --rmi all -v
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
echo 当前开发项目：全部容器已经重启
echo ==============================
pause