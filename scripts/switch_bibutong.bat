bat
@echo off
cd D:\crh123dexiaohao\server
echo 停止所有其他业务容器
docker compose -f docker-compose.business-bibutong.yml down
echo 启动Bibutong前端、后端、AI向量服务
docker compose -f docker-compose.business-bibutong.yml --env-file .env.dev up -d

:: docker compose --env-file .env.dev -f docker-compose.base.yml -f docker-compose.business-bibutong.yml up -d --build bibutong-backend
:: docker compose --env-file .env.dev -f docker-compose.base.yml -f docker-compose.business-bibutong.yml down
:: docker compose --env-file .env.dev -f docker-compose.base.yml -f docker-compose.business-bibutong.yml up -d --build
:: docker compose --env-file .env.dev -f docker-compose.base.yml -f docker-compose.business-bibutong.dev.yml up -d --build
:: docker compose --env-file .env.dev -f docker-compose.base.yml -f docker-compose.business-bibutong.dev.yml up -d --build bibutong-backend
:: docker compose -f docker-compose.base.yml -f docker-compose.business-bibutong.yml down

:: docker compose -f docker-compose.base.yml -f docker-compose.business-bibutong.dev.yml up -d --build
:: docker compose -f docker-compose.base.yml -f docker-compose.business-bibutong.dev.yml up -d --build bibutong-front
:: docker ps
:: docker ps
:: # 清理旧构建缓存、故障容器
:: docker builder prune -f

echo ==============================
echo 当前开发项目：Bibutong（含Llama AI推理）
echo 无需AI可执行ai_switch.bat关闭llama容器释放内存
echo ==============================
pause