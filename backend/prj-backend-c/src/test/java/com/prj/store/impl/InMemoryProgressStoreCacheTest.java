package com.prj.store.impl;

import com.prj.store.IProgressStore;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 由历史 CompareControllerCacheTest 迁移而来：原测试通过反射校验 CompareController 的 static
 * RESULT_CACHE/PROGRESS_CACHE 清理契约（F-01 内存泄漏修复）。重构后进度/结果改由
 * InMemoryProgressStore（Spring 单例 bean，双 ConcurrentHashMap）承载，本测试校验其清理契约：
 * 1) cancel 能同时移除进度与结果（避免结果 Map 无限增长）；
 * 2) 反复 save/cancel 不无限增长（size 回落为 0）；
 * 3) 移除不存在的 key 为安全 no-op。
 */
class InMemoryProgressStoreCacheTest
{
    private final IProgressStore store = new InMemoryProgressStore();

    private List<CompareResultRow> sampleResult()
    {
        return Collections.singletonList(new CompareResultRow("a", "a", "a", "a", 1.0, "完全匹配"));
    }

    @Test
    @DisplayName("清理契约：写入结果后 cancel(username) 同时移除进度与结果")
    void cancelRemovesBothProgressAndResult()
    {
        ProgressVo vo = new ProgressVo();
        vo.setStage("done");
        store.saveProgress("alice", vo);
        store.saveResult("alice", sampleResult());

        if (store.getProgress("alice") == null) fail("saveProgress 后应可读到进度");
        if (store.getResult("alice") == null) fail("saveResult 后应可读到结果");

        store.cancel("alice");

        assertNull(store.getProgress("alice"), "cancel 后应移除进度");
        assertNull(store.getResult("alice"), "cancel 后应移除结果（避免结果 Map 内存泄漏）");
    }

    @Test
    @DisplayName("清理契约：反复 save/cancel 不无限增长——1000 用户比对后全部清理，size 回落")
    void noUnboundedGrowth_underRepeatedSaveCancel()
    {
        int users = 1000;
        for (int i = 0; i < users; i++)
        {
            store.saveProgress("user-" + i, new ProgressVo());
            store.saveResult("user-" + i, sampleResult());
        }
        for (int i = 0; i < users; i++)
        {
            store.cancel("user-" + i);
        }
        for (int i = 0; i < users; i++)
        {
            assertNull(store.getProgress("user-" + i));
            assertNull(store.getResult("user-" + i));
        }
    }

    @Test
    @DisplayName("清理契约：移除不存在的 key 为安全 no-op")
    void removeMissingKey_isNoOp()
    {
        assertDoesNotThrow(() -> store.cancel("never-existed"));
        assertNull(store.getProgress("never-existed"));
    }
}
