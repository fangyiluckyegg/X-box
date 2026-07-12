@echo off
cd D:\crh123dexiaohao\X-box
docker compose -f docker-compose.base.yml -f docker-compose.business-prj.dev.yml down
echo Prj业务容器已全部停止，网关、MySQL、Redis仍常驻运行
pause