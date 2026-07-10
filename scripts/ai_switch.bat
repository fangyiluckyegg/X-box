@echo off
cd D:\crh123dexiaohao\X-box
echo 可选操作：
echo 1、停止AI推理容器 输入 stop
echo 2、启动AI推理容器 输入 start
set /p op=请输入指令：
if %op%==stop (
    docker stop dev-prj-llama
    echo AI容器已关闭，释放内存
)
if %op%==start (
    docker start dev-prj-llama
    echo AI向量服务已启动
)
pause