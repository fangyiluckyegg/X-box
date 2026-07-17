package com.prj.store.impl;

import com.prj.store.IProgressStore;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 进度/结果的内存实现（双 ConcurrentHashMap + expireAt）。
 *
 * <p>替代原 CompareController 的 {@code static ConcurrentHashMap} + 单线程清理器，
 * 彻底消除类加载期静态状态（水平扩展前提）。5 分钟 TTL，由 bean 内守护线程每 60s 扫描过期并清除。
 *
 * <p>活性续期：每次 {@link #getProgress}/{@link #getResult} 命中时刷新 TTL，
 * 故"前端持续轮询"的任务不会在运行中过期；停止轮询 5 分钟后自动回收。
 */
@Component
public class InMemoryProgressStore implements IProgressStore
{
    private static final Logger logger = LoggerFactory.getLogger(InMemoryProgressStore.class);

    /** 进度/结果存活时长（毫秒），超时由清理线程回收。 */
    private static final long TTL_MS = 5L * 60 * 1000;

    private final Map<String, ProgressVo> progressMap = new ConcurrentHashMap<>();
    private final Map<String, List<CompareResultRow>> resultMap = new ConcurrentHashMap<>();
    private final Map<String, Long> expireAtMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init()
    {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "inmemory-progress-cleaner");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanExpired, 60, 60, TimeUnit.SECONDS);
        logger.info("InMemoryProgressStore 清理调度已启动（TTL={}ms，扫描周期=60s）", TTL_MS);
    }

    @PreDestroy
    public void destroy()
    {
        if (scheduler != null)
        {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void saveProgress(String username, ProgressVo progress)
    {
        if (username == null || progress == null)
        {
            return;
        }
        progressMap.put(username, progress);
        refreshExpire(username);
    }

    @Override
    public void saveResult(String username, List<CompareResultRow> result)
    {
        if (username == null)
        {
            return;
        }
        resultMap.put(username, result);
        refreshExpire(username);
    }

    @Override
    public ProgressVo getProgress(String username)
    {
        if (username == null)
        {
            return null;
        }
        ProgressVo vo = progressMap.get(username);
        if (vo == null)
        {
            return null;
        }
        if (isExpired(username))
        {
            return null;
        }
        // 活性续期：前端持续轮询则任务保持存活
        refreshExpire(username);
        return vo;
    }

    @Override
    public List<CompareResultRow> getResult(String username)
    {
        if (username == null)
        {
            return null;
        }
        if (isExpired(username))
        {
            return null;
        }
        refreshExpire(username);
        return resultMap.get(username);
    }

    @Override
    public void markFailed(String username, String message)
    {
        if (username == null)
        {
            return;
        }
        ProgressVo vo = progressMap.get(username);
        if (vo == null)
        {
            vo = new ProgressVo();
        }
        vo.setStage("failed");
        vo.setMessage(message);
        vo.setCurrentText("比对失败");
        progressMap.put(username, vo);
        refreshExpire(username);
        logger.warn("比对任务失败 username={}, message={}", username, message);
    }

    @Override
    public void cancel(String username)
    {
        if (username == null)
        {
            return;
        }
        progressMap.remove(username);
        resultMap.remove(username);
        expireAtMap.remove(username);
    }

    private void refreshExpire(String username)
    {
        expireAtMap.put(username, System.currentTimeMillis() + TTL_MS);
    }

    private boolean isExpired(String username)
    {
        Long exp = expireAtMap.get(username);
        return exp != null && exp <= System.currentTimeMillis();
    }

    /** 清理过期条目（由调度线程调用；包可见便于单测通过反射调用）。 */
    void cleanExpired()
    {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : expireAtMap.entrySet())
        {
            if (entry.getValue() <= now)
            {
                String u = entry.getKey();
                progressMap.remove(u);
                resultMap.remove(u);
                expireAtMap.remove(u);
                logger.debug("清理过期比对进度/结果 username={}", u);
            }
        }
    }
}
