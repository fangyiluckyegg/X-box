package com.prj.store;

import com.prj.store.impl.InMemoryProgressStore;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InMemoryProgressStore 单元测试：写入/读取/清理/TTL 契约。
 * 不启动 Spring、不依赖数据库，直接构造 bean 实例（调度线程按需由 init 触发，
 * 但本测试无需调度，TTL 通过手动拨动 expireAt + 调用 cleanExpired 验证）。
 */
class InMemoryProgressStoreTest
{
    private final InMemoryProgressStore store = new InMemoryProgressStore();

    @Test
    @DisplayName("saveProgress/getProgress 覆盖写与读取")
    void saveAndGetProgress()
    {
        ProgressVo vo = new ProgressVo();
        vo.setStage("vector_calc");
        vo.setPercent(30);
        store.saveProgress("alice", vo);
        ProgressVo got = store.getProgress("alice");
        assertNotNull(got);
        assertEquals("vector_calc", got.getStage());
        assertEquals(30, got.getPercent());
    }

    @Test
    @DisplayName("saveResult/getResult 写入与读取")
    void saveAndGetResult()
    {
        CompareResultRow row = new CompareResultRow("a", "a", "a", "a", 1.0, "完全匹配");
        store.saveResult("alice", Collections.singletonList(row));
        List<CompareResultRow> got = store.getResult("alice");
        assertNotNull(got);
        assertEquals(1, got.size());
        assertEquals("完全匹配", got.get(0).getDiffType());
    }

    @Test
    @DisplayName("getProgress/getResult 无任务返回 null")
    void noTask_returnsNull()
    {
        assertNull(store.getProgress("nobody"));
        assertNull(store.getResult("nobody"));
    }

    @Test
    @DisplayName("markFailed：stage=failed 且写入 message")
    void markFailed_setsFailedStageAndMessage()
    {
        ProgressVo vo = new ProgressVo();
        vo.setStage("vector_calc");
        store.saveProgress("alice", vo);
        store.markFailed("alice", "Ollama 超时，请检查向量服务");
        ProgressVo got = store.getProgress("alice");
        assertNotNull(got);
        assertEquals("failed", got.getStage());
        assertEquals("Ollama 超时，请检查向量服务", got.getMessage());
    }

    @Test
    @DisplayName("cancel：清空进度与结果")
    void cancel_clearsProgressAndResult()
    {
        ProgressVo vo = new ProgressVo();
        vo.setStage("done");
        store.saveProgress("alice", vo);
        store.saveResult("alice", Collections.singletonList(new CompareResultRow()));
        store.cancel("alice");
        assertNull(store.getProgress("alice"));
        assertNull(store.getResult("alice"));
    }

    @Test
    @DisplayName("TTL：过期条目被清理线程回收")
    void ttl_expiredEntriesCleaned()
    {
        ProgressVo vo = new ProgressVo();
        vo.setStage("done");
        store.saveProgress("alice", vo);
        // 将过期时间拨到过去
        @SuppressWarnings("unchecked")
        Map<String, Long> expireAt = (Map<String, Long>) ReflectionTestUtils.getField(store, "expireAtMap");
        assertNotNull(expireAt);
        expireAt.put("alice", System.currentTimeMillis() - 1000);
        // 触发清理
        ReflectionTestUtils.invokeMethod(store, "cleanExpired");
        assertNull(store.getProgress("alice"));
    }

    @Test
    @DisplayName("重提覆盖：新进度覆盖旧进度")
    void resubmit_overwritesProgress()
    {
        ProgressVo v1 = new ProgressVo();
        v1.setStage("vector_calc");
        store.saveProgress("alice", v1);
        ProgressVo v2 = new ProgressVo();
        v2.setStage("done");
        store.saveProgress("alice", v2);
        assertEquals("done", store.getProgress("alice").getStage());
    }

    @Test
    @DisplayName("活性续期：getProgress 命中（未过期）后刷新 TTL 到更晚的未来")
    void activityKeepAlive_refreshesTtl()
    {
        store.saveProgress("alice", new ProgressVo());
        @SuppressWarnings("unchecked")
        Map<String, Long> expireAt = (Map<String, Long>) ReflectionTestUtils.getField(store, "expireAtMap");
        assertNotNull(expireAt);
        long oldExp = System.currentTimeMillis() + 1000; // 未来、未过期
        expireAt.put("alice", oldExp);
        assertNotNull(store.getProgress("alice"));
        long newExp = expireAt.get("alice");
        assertTrue(newExp > oldExp, "getProgress 命中后应刷新 TTL 到更晚的未来");
    }
}
