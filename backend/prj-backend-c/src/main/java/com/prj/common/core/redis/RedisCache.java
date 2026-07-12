package com.prj.common.core.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


/**
 * Redis 缓存操作封装组件（Spring Bean）。
 *
 * <p>职责：
 * 对 Spring Data Redis 的 {@code RedisTemplate} 做轻量封装，提供最常用的
 * 基本对象（String/实体等）的写入、读取与删除能力，并支持过期时间设置。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code RedisTemplate}（由 RedisConfig 提供的 Bean）。
 * - 被依赖：{@code CaptchaController}（存/取验证码）、{@code TokenService}（存/取登录态）、
 *           {@code AccountLockService}（存登录失败计数）等。
 */
@Component
public class RedisCache
{
    @SuppressWarnings("rawtypes")
    @Autowired
    public RedisTemplate redisTemplate; 

    /** 缓存基本的对象，Integer、String、实体类等
     * @param key 缓存的键值
     * @param value 缓存的值
     * @param timeout 时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit)
    {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /** 获得缓存的基本对象。
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key)
    {
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(key);
    }

    /** 删除单个对象
     * @param key 缓存键
     * @return 是否删除成功
     */
    public boolean deleteObject(final String key)
    {
        return redisTemplate.delete(key);
    }

}
