package com.prj.framework.web.service;

import com.prj.common.core.redis.RedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccountLockService 单元测试（纯 Mockito，不加载 Spring 上下文）。
 * <p>
 * 验证 S8/S9 暴力破解防护核心逻辑：失败计数递增、锁定阈值判定、
 * 失败计数清除、剩余次数计算与锁定时长读取。Redis 交互通过 {@link RedisCache} mock 验证。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest
{
    private static final String USERNAME = "admin";
    private static final String LOCK_KEY = "login_fail:" + USERNAME;

    @Mock
    private RedisCache redisCache;

    @InjectMocks
    private AccountLockService accountLockService;

    @BeforeEach
    void setUp()
    {
        // @Value 字段由 Spring 注入，单元测试里用 ReflectionTestUtils 设置默认阈值/时长
        ReflectionTestUtils.setField(accountLockService, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(accountLockService, "lockTimeMinutes", 15);
    }

    @Test
    @DisplayName("recordLoginFailure：首次失败从 0 递增为 1，并设置 TTL 为锁定分钟数")
    void recordLoginFailure_firstFailure_incrementsAndSetsTtl()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(null);

        accountLockService.recordLoginFailure(USERNAME);

        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ttlCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(redisCache).setCacheObject(eq(LOCK_KEY), countCaptor.capture(), ttlCaptor.capture(), eq(TimeUnit.MINUTES));
        assertEquals(1, countCaptor.getValue());
        assertEquals(15, ttlCaptor.getValue());
    }

    @Test
    @DisplayName("recordLoginFailure：已有 3 次失败，再记一次递增为 4")
    void recordLoginFailure_incrementsFromExisting()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(3);

        accountLockService.recordLoginFailure(USERNAME);

        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(redisCache).setCacheObject(eq(LOCK_KEY), countCaptor.capture(), eq(15), eq(TimeUnit.MINUTES));
        assertEquals(4, countCaptor.getValue());
    }

    @Test
    @DisplayName("isLocked：失败次数达到阈值(5)返回 true")
    void isLocked_atThreshold_returnsTrue()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(5);
        assertTrue(accountLockService.isLocked(USERNAME));
    }

    @Test
    @DisplayName("isLocked：失败次数低于阈值(4)返回 false")
    void isLocked_belowThreshold_returnsFalse()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(4);
        assertFalse(accountLockService.isLocked(USERNAME));
    }

    @Test
    @DisplayName("isLocked：无失败记录(null)返回 false")
    void isLocked_noRecord_returnsFalse()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(null);
        assertFalse(accountLockService.isLocked(USERNAME));
    }

    @Test
    @DisplayName("clearFailures：删除失败计数键")
    void clearFailures_deletesKey()
    {
        accountLockService.clearFailures(USERNAME);
        verify(redisCache).deleteObject(LOCK_KEY);
    }

    @Test
    @DisplayName("getRemainingAttempts：已用 2 次，剩余 3 次")
    void getRemainingAttempts_returnsRemaining()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(2);
        assertEquals(3, accountLockService.getRemainingAttempts(USERNAME));
    }

    @Test
    @DisplayName("getRemainingAttempts：无记录剩余 5 次（等于阈值）")
    void getRemainingAttempts_noRecord_returnsMax()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(null);
        assertEquals(5, accountLockService.getRemainingAttempts(USERNAME));
    }

    @Test
    @DisplayName("getRemainingAttempts：已锁定(达到阈值)返回 0")
    void getRemainingAttempts_whenLocked_returnsZero()
    {
        when(redisCache.getCacheObject(LOCK_KEY)).thenReturn(5);
        assertEquals(0, accountLockService.getRemainingAttempts(USERNAME));
    }

    @Test
    @DisplayName("getLockMinutes：返回配置的锁定分钟数")
    void getLockMinutes_returnsConfiguredValue()
    {
        assertEquals(15, accountLockService.getLockMinutes());
    }
}
