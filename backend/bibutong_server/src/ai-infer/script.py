import sys

def main():
    # 获取命令行参数
    if len(sys.argv) >= 4:
        arg1 = sys.argv[1]
        arg2 = sys.argv[2]
        arg3 = int(sys.argv[3])
        
        result = f"{arg1} {arg2} - {arg3 * 2}"
        print(result)  # 输出到标准输出
    else:
        print("参数不足")

if __name__ == "__main__":
    main()