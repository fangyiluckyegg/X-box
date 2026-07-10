@echo off
cd D:\crh123dexiaohao\X-box
echo 启动公共Nginx、MySQL、Redis常驻服务
docker compose -f docker-compose.base.yml --env-file .env.dev up -d
echo 公共中间件启动完成，可执行switch_prj开启业务项目
pause