@echo off
cd D:\crh123dexiaohao\X-box
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml down
echo 业务容器已全部停止
pause