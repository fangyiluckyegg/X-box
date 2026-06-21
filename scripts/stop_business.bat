@echo off
cd D:\crh123dexiaohao\server
docker compose -f docker-compose.business-bibutong.yml down
echo Bibutong业务容器已全部停止，网关、MySQL、Redis仍常驻运行
pause