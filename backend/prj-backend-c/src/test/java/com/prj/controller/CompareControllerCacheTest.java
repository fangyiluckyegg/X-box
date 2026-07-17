package com.prj.controller;

import com.prj.store.IProgressStore;
import com.prj.store.impl.InMemoryProgressStore;
import com.prj.web.vo.ProgressVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 由历史 CompareControllerCacheTest 迁移而来。
 *
 * <p>原测试通过反射校验 CompareController 的 static RESULT_CACHE/PROGRESS_CACHE 清理契约；
 * 重构后进度/结果改由 Spring 单例 IProgressStore（InMemoryProgressStore）承载，
 * God Controller 不再持有 static 状态。本测试作为回归护栏：
 * 1) 校验 CompareController 已无 static 缓存/调度字段（消除类加载期静态状态 / 可水平扩展前提）；
 * 2) 校验 IProgressStore 的清理契约（cancel 同时回收进度与结果、无任务 getProgress 返回 null）。
 */
class CompareControllerCacheTest
{
    @Test
    @DisplayName("回归护栏：CompareController 不再持有 static 缓存/调度字段")
    void controller_hasNoStaticCacheFields()
    {
        for (Field f : CompareController.class.getDeclaredFields())
        {
            boolean isStatic = Modifier.isStatic(f.getModifiers());
            if (isStatic && (f.getName().contains("CACHE")
                    || f.getName().contains("SCHEDULER")
                    || f.getName().contains("CLEANUP")))
            {
                fail("重构后 CompareController 仍存在静态状态字段: " + f.getName());
            }
        }
    }

    @Test
    @DisplayName("清理契约：IProgressStore.cancel 同时回收进度与结果，无任务 getProgress 返回 null")
    void storeCleanupContract()
    {
        IProgressStore store = new InMemoryProgressStore();
        ProgressVo vo = new ProgressVo();
        vo.setStage("done");
        store.saveProgress("alice", vo);

        assertNotNull(store.getProgress("alice"));
        store.cancel("alice");
        assertNull(store.getProgress("alice"));
        assertNull(store.getProgress("bob"));
    }
}
