package com.prj.service.similarity.impl;

import com.prj.service.similarity.ISimilarityService;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 相似度比对实现（纯函数，可独立单测）。
 *
 * <p>余弦相似度计算 + 四分类（完全匹配 / 语义模糊匹配 / 未匹配 / 新增项）。
 * 冻结 {@code SIMILARITY_THRESHOLD=0.85}（语义模糊匹配阈值）。
 * new 侧覆盖率校验留给比对编排（{@code CompareServiceImpl}）处理。
 */
@Service
public class SimilarityServiceImpl implements ISimilarityService
{
    /** 判定为"语义模糊匹配"的相似度阈值（>= 0.85）。 */
    private static final double SIMILARITY_THRESHOLD = 0.85;

    @Override
    public double cosine(double[] a, double[] b)
    {
        if (a == null || b == null || a.length == 0 || b.length == 0)
        {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++)
        {
            dot += a[i] * b[i];
            na += Math.pow(a[i], 2);
            nb += Math.pow(b[i], 2);
        }
        double norm = Math.sqrt(na) * Math.sqrt(nb);
        return norm == 0.0 ? 0.0 : dot / norm;
    }

    @Override
    public List<CompareResultRow> compare(List<String> originTexts,
                                          List<String> newTexts,
                                          Map<String, double[]> originVecMap,
                                          Map<String, double[]> newVecMap,
                                          ProgressVo progress)
    {
        List<CompareResultRow> result = new ArrayList<>();
        boolean[] matchedFlag = new boolean[newTexts.size()];
        Map<String, double[]> newVecCache = newVecMap;

        int total = originTexts.size();
        int done = 0;

        for (String originText : originTexts)
        {
            double[] oVec = originVecMap.get(originText);
            int bestIdx = -1;
            double maxSim = 0.0;

            // 在比对文本中找与当前原始文本余弦相似度最高的项
            for (int i = 0; i < newTexts.size(); i++)
            {
                String nt = newTexts.get(i);
                double[] nVec = newVecCache.get(nt);
                if (nVec == null) continue;
                double sim = cosine(oVec, nVec);
                if (sim > maxSim)
                {
                    maxSim = sim;
                    bestIdx = i;
                }
            }

            CompareResultRow row = new CompareResultRow();
            row.setName(originText);
            row.setOriginVal(originText);
            if (bestIdx == -1)
            {
                row.setMatchedName("");
                row.setNewVal("");
                row.setSimilarity(0.0);
                row.setDiffType("未匹配");
            }
            else
            {
                String bestText = newTexts.get(bestIdx);
                // 文本完全一致 -> 完全匹配（相似度记 1.0）
                if (originText.equalsIgnoreCase(bestText.trim()))
                {
                    matchedFlag[bestIdx] = true;
                    row.setMatchedName(bestText);
                    row.setNewVal(bestText);
                    row.setSimilarity(1.0);
                    row.setDiffType("完全匹配");
                }
                // 相似度达到阈值 -> 语义模糊匹配
                else if (maxSim >= SIMILARITY_THRESHOLD)
                {
                    matchedFlag[bestIdx] = true;
                    row.setMatchedName(bestText);
                    row.setNewVal(bestText);
                    row.setSimilarity(Math.round(maxSim * 100) / 100.0);
                    row.setDiffType("语义模糊匹配");
                }
                // 低于阈值 -> 视为未匹配
                else
                {
                    row.setMatchedName("");
                    row.setNewVal("");
                    row.setSimilarity(Math.round(maxSim * 100) / 100.0);
                    row.setDiffType("未匹配");
                }
            }
            result.add(row);
            done++;
            if (progress != null)
            {
                progress.setCurrent(done);
                progress.setCurrentText(originText);
                progress.setPercent(total > 0 ? (int) Math.round(done * 100.0 / total) : 0);
            }
        }

        // 新增条目：遍历比对文本，凡未被任何原始文本匹配上的，记为"新增项"
        if (progress != null)
        {
            progress.setCurrentText("扫描新增条目");
        }
        for (int i = 0; i < newTexts.size(); i++)
        {
            if (!matchedFlag[i])
            {
                String nt = newTexts.get(i);
                CompareResultRow row = new CompareResultRow();
                row.setName("");
                row.setOriginVal("");
                row.setMatchedName(nt);
                row.setNewVal(nt);
                row.setSimilarity(0.0);
                row.setDiffType("新增项");
                result.add(row);
            }
        }
        return result;
    }
}
