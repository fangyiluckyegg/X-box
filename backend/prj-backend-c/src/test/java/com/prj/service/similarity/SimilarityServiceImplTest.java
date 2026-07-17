package com.prj.service.similarity;

import com.prj.service.similarity.impl.SimilarityServiceImpl;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SimilarityServiceImpl 纯函数单元测试：cosine 计算 + 四分类（完全匹配/语义模糊匹配/未匹配/新增项）。
 */
class SimilarityServiceImplTest
{
    private SimilarityServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new SimilarityServiceImpl();
    }

    @Test
    @DisplayName("cosine：正交向量为 0，相同向量为 1，零向量/ null 为 0")
    void cosine_basic()
    {
        assertEquals(0.0, service.cosine(new double[]{1, 0}, new double[]{0, 1}), 1e-9);
        assertEquals(1.0, service.cosine(new double[]{1, 0}, new double[]{1, 0}), 1e-9);
        assertEquals(0.0, service.cosine(new double[]{0, 0}, new double[]{1, 0}), 1e-9);
        assertEquals(0.0, service.cosine(null, new double[]{1, 0}), 1e-9);
    }

    @Test
    @DisplayName("compare：覆盖 完全匹配/语义模糊匹配/未匹配/新增项 四分类")
    void compare_fourCategories()
    {
        List<String> origin = Arrays.asList("apple", "banana", "cat");
        List<String> neu = Arrays.asList("Apple", "banana2", "dog", "zebra");

        Map<String, double[]> originVec = new HashMap<>();
        originVec.put("apple", new double[]{1, 0});
        originVec.put("banana", new double[]{0, 1});
        originVec.put("cat", new double[]{1, 1});   // 与所有 new 向量余弦 < 0.85 → 未匹配

        Map<String, double[]> newVec = new HashMap<>();
        newVec.put("Apple", new double[]{1, 0});     // 文本相等 → 完全匹配
        newVec.put("banana2", new double[]{0, 1});   // 向量同 banana 但文本不同 → 语义模糊匹配
        newVec.put("dog", new double[]{0, 0});       // 零向量 → 无 origin 匹配 → 新增项
        newVec.put("zebra", new double[]{0, 1});     // 无 origin 文本相等/高相似匹配 → 新增项

        List<CompareResultRow> rows = service.compare(origin, neu, originVec, newVec, new ProgressVo());

        // origin 3 行 + 新增项（dog, zebra）2 行 = 5 行
        assertEquals(5, rows.size());

        Map<String, CompareResultRow> byName = new LinkedHashMap<>();
        for (CompareResultRow r : rows)
        {
            if (!r.getName().isEmpty())
            {
                byName.put(r.getName(), r);
            }
        }

        assertEquals("完全匹配", byName.get("apple").getDiffType());
        assertEquals(1.0, byName.get("apple").getSimilarity(), 1e-9);

        assertEquals("语义模糊匹配", byName.get("banana").getDiffType());
        assertEquals(1.0, byName.get("banana").getSimilarity(), 1e-9);

        assertEquals("未匹配", byName.get("cat").getDiffType());
        // 注：未匹配项保留其最高(sub-threshold)相似度供用户参考，此处约为 0.71，不应为 0。
        // 与「新增项」(无任何候选、相似度 0.0) 区分，体现「近失配」语义。
        double catSimilarity = byName.get("cat").getSimilarity();
        assertTrue(catSimilarity > 0.0 && catSimilarity < 0.85,
                "未匹配项应保留 sub-threshold 最高相似度（约 0.71），而非 0");

        long added = rows.stream().filter(r -> r.getDiffType().equals("新增项")).count();
        assertEquals(2, added, "dog 与 zebra 均应判定为新增项");
        long unmatched = rows.stream().filter(r -> r.getDiffType().equals("未匹配")).count();
        assertEquals(1, unmatched, "仅 origin 的 cat 为未匹配");
    }

    @Test
    @DisplayName("compare：progress 被回写 current/currentText/percent")
    void compare_updatesProgress()
    {
        List<String> origin = Arrays.asList("a", "b");
        List<String> neu = Arrays.asList("a", "b");
        Map<String, double[]> v = new HashMap<>();
        v.put("a", new double[]{1, 0});
        v.put("b", new double[]{0, 1});
        ProgressVo progress = new ProgressVo();
        service.compare(origin, neu, v, v, progress);
        assertEquals(2, progress.getCurrent());
        assertEquals(100, progress.getPercent());
        assertEquals("扫描新增条目", progress.getCurrentText());
    }

    @Test
    @DisplayName("compare：progress 传 null 时不做回写且不抛异常")
    void compare_nullProgress_safe()
    {
        List<String> origin = Arrays.asList("a");
        List<String> neu = Arrays.asList("a");
        Map<String, double[]> v = new HashMap<>();
        v.put("a", new double[]{1, 0});
        List<CompareResultRow> rows = service.compare(origin, neu, v, v, null);
        assertEquals(1, rows.size());
        assertEquals("完全匹配", rows.get(0).getDiffType());
    }
}
