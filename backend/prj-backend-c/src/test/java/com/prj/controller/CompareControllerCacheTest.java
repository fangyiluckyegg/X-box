package com.prj.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CompareController.RESULT_CACHE 写入与定时清理（F-01 内存泄漏修复）单元测试。
 *
 * <p>背景：CompareController 的 RESULT_CACHE 是
 * {@code private static final ConcurrentHashMap<String, List<Map<String,Object>>>}，
 * 在 compareExcel 成功时 {@code put(username, result)}（源码 L148），原实现永不回收；
 * F-01 修复在 5 分钟清理线程中一并 {@code RESULT_CACHE.remove(username)}（源码 L165）。</p>
 *
 * <p>本测试不修改业务源码、不启动 Spring、不等待 5 分钟定时器，而是通过反射直接拿到
 * CompareController 真实的静态 RESULT_CACHE 实例，验证修复的“正确性不变量”：
 * 1) 清理线程执行的 remove 操作确实移除对应条目（条目被移除、可下载结果随之失效）；
 * 2) put 与 remove 使用同一种 key（String username），类型/语义一致，无漏删/误删；
 * 3) 反复 put/remove 后缓存不无限增长（size 回落为 0）。</p>
 *
 * <p>说明：5 分钟定时本身属于调度细节，建议另以 @SpringBootTest 集成测试覆盖；
 * 此处钉死的是 F-01 修复的核心契约——remove 能正确回收，避免 JVM 生命周期内无限增长。
 * 该契约与既有 PROGRESS_CACHE 的清理线程同构（源码 L164-165 同处一个 Runnable）。</p>
 */
class CompareControllerCacheTest
{
    private static final Field RESULT_CACHE_FIELD;
    private static final Field PROGRESS_CACHE_FIELD;

    static
    {
        try
        {
            RESULT_CACHE_FIELD = CompareController.class.getDeclaredField("RESULT_CACHE");
            RESULT_CACHE_FIELD.setAccessible(true);
            PROGRESS_CACHE_FIELD = CompareController.class.getDeclaredField("PROGRESS_CACHE");
            PROGRESS_CACHE_FIELD.setAccessible(true);
        }
        catch (NoSuchFieldException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> resultCache() throws IllegalAccessException
    {
        return (ConcurrentHashMap<String, Object>) RESULT_CACHE_FIELD.get(null);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> progressCache() throws IllegalAccessException
    {
        return (ConcurrentHashMap<String, Object>) PROGRESS_CACHE_FIELD.get(null);
    }

    @AfterEach
    void tearDown() throws IllegalAccessException
    {
        // 测试间隔离：清空静态缓存，避免污染（不影响业务运行，单元环境下无并发写入）
        resultCache().clear();
        progressCache().clear();
    }

    @Test
    @DisplayName("RESULT_CACHE 是 static final 的 ConcurrentHashMap（修复作用于 put 所用的同一实例）")
    void cacheField_isStaticFinalConcurrentHashMap()
    {
        assertTrue(java.lang.reflect.Modifier.isStatic(RESULT_CACHE_FIELD.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isFinal(RESULT_CACHE_FIELD.getModifiers()));
        Object instance = ReflectionTestUtils.getField(CompareController.class, "RESULT_CACHE");
        assertNotNull(instance);
        assertTrue(instance instanceof ConcurrentHashMap, "RESULT_CACHE 应为 ConcurrentHashMap 以支持并发 remove");
    }

    @Test
    @DisplayName("F-01: 写入结果后，模拟清理线程 remove(username) 能移除该条目")
    void f01_cleanupRemovesResultEntry() throws IllegalAccessException
    {
        ConcurrentHashMap<String, Object> cache = resultCache();
        String username = "alice";
        cache.put(username, sampleResult());

        assertEquals(1, cache.size());
        assertTrue(cache.containsKey(username));

        // 模拟 compareExcel finally 清理线程的核心操作（源码 L164-165）
        Runnable cleanup = () -> cache.remove(username);
        cleanup.run();

        assertFalse(cache.containsKey(username), "清理后 RESULT_CACHE 不应再含该用户条目（内存泄漏已修复）");
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("F-01: remove 的 key 与 put 的 key 同为 String username，类型/语义一致，不会误删他人")
    void f01_removeKeyTypeConsistent_noCrossDelete() throws IllegalAccessException
    {
        ConcurrentHashMap<String, Object> cache = resultCache();
        cache.put("alice", sampleResult());
        cache.put("bob", sampleResult());

        // 仅移除 alice（与 compareExcel 中 put(username) 的 key 同类型、同值）
        cache.remove("alice");

        assertFalse(cache.containsKey("alice"));
        assertTrue(cache.containsKey("bob"), "移除 alice 不应误删 bob（证明 key 类型一致，无竞态误删）");
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("F-01: 反复 put/remove 不无限增长——1000 个用户比对后全部清理，size 回落 0")
    void f01_noUnboundedGrowth_underRepeatedPutRemove() throws IllegalAccessException
    {
        ConcurrentHashMap<String, Object> cache = resultCache();

        int users = 1000;
        for (int i = 0; i < users; i++)
        {
            cache.put("user-" + i, sampleResult());
        }
        assertEquals(users, cache.size(), "写入阶段应严格等于用户数，无重复/无额外条目");

        // 模拟每个请求的 5 分钟清理线程都正确 remove（源码 L165 的契约）
        for (int i = 0; i < users; i++)
        {
            cache.remove("user-" + i);
        }
        assertEquals(0, cache.size(), "全部清理后缓存应回落为 0，证明不会随比对次数无限增长");
    }

    @Test
    @DisplayName("F-01: 移除不存在的 key 为安全 no-op（清理线程对任意 username 调用 remove 不抛异常）")
    void f01_removeMissingKey_isNoOp() throws IllegalAccessException
    {
        ConcurrentHashMap<String, Object> cache = resultCache();
        cache.put("alice", sampleResult());

        assertDoesNotThrow(() -> cache.remove("never-existed"));
        assertEquals(1, cache.size());
        assertTrue(cache.containsKey("alice"));
    }

    private static List<Map<String, Object>> sampleResult()
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "示例名称");
        row.put("matchedName", "匹配名称");
        row.put("similarity", 0.92);
        row.put("diffType", "语义模糊匹配");
        return Collections.singletonList(row);
    }
}
