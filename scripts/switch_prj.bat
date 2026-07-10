@echo off
cd D:\crh123dexiaohao\X-box
echo 停止所有其他业务容器
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml down
echo 启动Prj前端、后端、AI向量服务
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml --env-file .env.dev up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build prj-frontend
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build prj-backend-c
:: docker compose -f docker-compose.business-prj.dev.yml restart prj-backend-c
:: docker compose -f docker-compose.business-prj.dev.yml restart nginx-gateway
:: docker compose -f docker-compose.business-prj.dev.yml restart dev-prj-llama
:: docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml up -d --build
:: docker ps
:: # 清理旧构建缓存、故障容器
:: docker builder prune -f
:: 清理旧容器镜像
:: docker-compose down --rmi all -v

echo ==============================
echo 当前开发项目：Prj（含Llama AI推理）
echo 无需AI可执行ai_switch.bat关闭llama容器释放内存
echo ==============================
pause