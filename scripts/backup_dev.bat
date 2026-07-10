@echo off
cd D:\crh123dexiaohao\X-box
set dt=%date:~0,4%%date:~5,2%%date:~8,2%
:: 备份MySQL开发库
docker exec dev-mysql mysqldump -uroot -pRoot@Dev123456 prj_dev > backup/prj_dev_%dt%.sql
:: 备份编排与Nginx配置
xcopy gateway backup/gateway_%dt% /E /I /Y
xcopy *.yml backup/compose_%dt% /Y
echo 备份文件存放至 ./backup 目录
pause