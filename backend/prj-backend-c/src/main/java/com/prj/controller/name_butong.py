import pandas as pd
import ollama
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from datetime import datetime
import sys
sys.stdout.reconfigure(encoding='utf-8')

# 读取Excel文件（修改为实际路径）
df1 = pd.read_excel('D:/crh123dexiaohao/server/backend/prj-backend-c/uploadTemp/doc/names_bak.xlsx')

# 读取比对表数据
df2 = pd.read_excel('D:/crh123dexiaohao/server/backend/prj-backend-c/uploadTemp/doc/names_new.xlsx')

# 获取唯一单位列表
unit1 = df1['danwei_name'].dropna().unique().tolist()
print(unit1)
unit2 = df2['danwei_name'].dropna().unique().tolist()
print(unit2)

print("正在预处理数据...")
# 第一步筛选：剔除unit2中与unit1中完全相同的单位名称
exact_diff = list(set(unit2) - set(unit1))
print(exact_diff) 

if not exact_diff:  # 如何unit2中单位名称完全在uint1中，程序退出
    print("所有单位名称完全匹配")
    exit()

print("正在生成文件1的语义向量...")
# 为unit1生成所有嵌入向量
embeddings1 = []
for u in unit1:
    #print(u)    
    try:
        response = ollama.embeddings(model='bge-m3:latest', prompt=u)
        embeddings1.append(response['embedding'])
    except Exception as e:
        print(f"生成向量失败：{u} - {str(e)}")
        continue

if not embeddings1:
    print("文件1没有有效数据")
    exit()

embeddings1_array = np.array(embeddings1)
print("正在分析语义差异...")
#print(embeddings1) # 显示全量向量结果
print(embeddings1_array) # 显示全量向量矩阵化

threshold = 0.85  # 相似度阈值（根据实际效果调整）
semantic_diff = []

for candidate in exact_diff:
    print(candidate)
    try:
        # 生成候选单位向量
        #response = ollama.embeddings(model='mxbai-embed-large:latest', prompt=candidate)
        response = ollama.embeddings(model='bge-m3:latest', prompt=candidate)
         
        candidate_vec = np.array(response['embedding']).reshape(1, -1)
        
        # 计算最大相似度
        similarities = cosine_similarity(candidate_vec, embeddings1_array)
        max_sim = np.max(similarities)
        print(max_sim)
        
        if max_sim < threshold:
            semantic_diff.append(candidate)
    except Exception as e:
        print(f"处理失败：{candidate} - {str(e)}")
        continue

# 输出最终结果
print("\n文件2中的实际不同单位名称：")
for i, unit in enumerate(semantic_diff, 1):
    print(f"{i}. {unit}")

current_time = datetime.now()

# 可选：保存结果到新文件
data_to_import = pd.DataFrame({'danwei_name': semantic_diff,'bidui_date': current_time,})
data_to_import.to_excel('D:/crh123dexiaohao/server/backend/prj-backend-c/uploadTemp/doc/names_budong.xlsx', index=False)

