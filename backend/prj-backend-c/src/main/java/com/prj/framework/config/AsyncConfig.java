package com.prj.framework.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.TimeUnit;

/**
 * 异步与 HTTP 客户端配置（框架级）。
 *
 * <p>职责：
 * 1) 启用 {@code @EnableAsync}，提供有界 {@link ThreadPoolTaskExecutor}（核心=最大=4、队列=64、
 *    拒绝策略打明确告警日志），替代 Spring 默认无界 {@code SimpleAsyncTaskExecutor}，
 *    防止比对风暴拖垮 Tomcat 业务线程（可用性 DoS 根因）。
 * 2) 提供复用的 {@link OkHttpClient} 单例 bean（连接 30s / 读取 120s / 连接池 20），
 *    供 {@code OkHttpOllamaEmbedClient} 注入，连接池复用降低握手开销。
 */
@Configuration
@EnableAsync
public class AsyncConfig
{
    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * 比对异步任务线程池（有界，避免无界增长）。
     *
     * @return 命名 compareTaskExecutor 的有界线程池
     */
    @Bean(name = "compareTaskExecutor")
    public ThreadPoolTaskExecutor compareTaskExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("compare-async-");
        // 拒绝策略：仅打明确告警日志（不抛异常中断主流程），由前端重试机制兜底
        executor.setRejectedExecutionHandler((r, exec) ->
        {
            logger.warn("比对任务线程池已满（核心=4/队列=64），拒绝新任务，触发告警；请稍后重试或扩容线程池");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 复用的 OkHttpClient 单例（连接 30s / 读取 120s / 连接池 20 连接）。
     *
     * @return OkHttpClient 实例
     */
    @Bean
    public OkHttpClient okHttpClient()
    {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                // 批量嵌入单请求可能处理 EMBED_BATCH_SIZE 条文本，放宽读取超时到 120s 避免大批量超时
                .readTimeout(120, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(20, 30, TimeUnit.SECONDS))
                .build();
    }
}
