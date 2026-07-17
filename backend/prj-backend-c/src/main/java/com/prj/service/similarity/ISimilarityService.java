package com.prj.service.similarity;

import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;

import java.util.List;
import java.util.Map;

/**
 * 相似度比对服务接口。
 *
 * <p>提供余弦相似度计算与"原始 vs 新"的四分类（完全匹配 / 语义模糊匹配 / 未匹配 / 新增项）比对。
 * 纯函数实现（{@link com.prj.service.similarity.impl.SimilarityServiceImpl}），便于独立单测。
 *
 * <p>注：{@link #compare} 额外接收 {@link ProgressVo} 用于回写逐条进度（current/currentText/percent），
 * 与重构前 CompareController.matchProcess 的渐进式进度保持一致的用户体验；传 null 则不做回写（便于纯函数测试）。
 */
public interface ISimilarityService
{
    /**
     * 对原始文本逐条在比对文本中寻找最相似项，并标记未匹配与新增项。
     *
     * @param originTexts  原始文本列表（基准）
     * @param newTexts     比对文本列表（新版本）
     * @param originVecMap 原始文本->向量映射
     * @param newVecMap    比对文本->向量映射
     * @param progress     进度对象（可选；非 null 时回写 current/currentText/percent 供前端轮询）
     * @return 比对结果行列表
     */
    List<CompareResultRow> compare(List<String> originTexts,
                                   List<String> newTexts,
                                   Map<String, double[]> originVecMap,
                                   Map<String, double[]> newVecMap,
                                   ProgressVo progress);

    /**
     * 计算两个向量的余弦相似度（取值 0~1，越接近 1 越相似）。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 余弦相似度；任一侧为零向量或 null 时返回 0
     */
    double cosine(double[] a, double[] b);
}
