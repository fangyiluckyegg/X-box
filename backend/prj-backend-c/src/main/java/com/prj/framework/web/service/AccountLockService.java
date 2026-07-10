package com.prj.framework.web.service;

import com.prj.common.core.redis.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 账号登录失败锁定服务。
 * <p>
 * 承载暴力破解防护逻辑，维护基于 Redis 的失败计数。连续失败达到阈值后，
 * 该账号被视为锁定 {@code lockTimeMinutes} 分钟。登录成功时调用 {@link #clearFailures(String)} 清除计数。
 * </p>
 * <p>
 * [C8] 在原有<b>用户名维度</b>基础上新增<b>IP 维度</b>失败计数与锁定，
 * 两者并存：任一维度达到阈值即视为锁定，登录成功时两个维度计数同时清除。
 * IP 维度采用<b>递增锁定时长</b>（失败越多次，锁定越久），进一步抬高暴力破解成本。
 * </p>
 * <p>
 * 阈值与锁定时长可通过配置覆盖（默认值与历史 {@code LoginService} 中硬编码保持一致）：
 * <ul>
 *   <li>{@code security.login-max-attempts}（默认 5）</li>
 *   <li>{@code security.login-lock-minutes}（默认 15）</li>
 * </ul>
 * </p>
 */
@Service
public class AccountLockService
{
    /** Redis 中用户名维度失败计数键前缀（与历史实现保持一致）。 */
    private static final String LOGIN_FAIL_KEY = "login_fail:";
    /** Redis 中 IP 维度失败计数键前缀（C8 新增）。 */
    private static final String LOGIN_FAIL_IP_KEY = "login_fail_ip:";

    /** 允许的最大连续失败次数，达到即视为锁定。 */
    @Value("${security.login-max-attempts:5}")
    private int maxLoginAttempts;

    /** 锁定时长（分钟），同时作为用户名维度失败计数的 Redis TTL。 */
    @Value("${security.login-lock-minutes:15}")
    private int lockTimeMinutes;

    @Autowired
    private RedisCache redisCache;

    /**
     * 判断账号是否已被锁定（失败次数达到阈值）。
     *
     * @param username 用户名
     * @return 已锁定返回 {@code true}，否则返回 {@code false}
     */
    public boolean isLocked(String username)
    {
        Integer failCount = redisCache.getCacheObject(LOGIN_FAIL_KEY + username);
        return failCount != null && failCount >= maxLoginAttempts;
    }

    /**
     * [C8] 判断客户端 IP 是否已被锁定（IP 维度失败次数达到阈值）。
     *
     * @param ip 客户端 IP（为 {@code null}/空时直接返回 {@code false}）
     * @return 已锁定返回 {@code true}，否则返回 {@code false}
     */
    public boolean isIpLocked(String ip)
    {
        if (ip == null || ip.isBlank())
        {
            return false;
        }
        Integer failCount = redisCache.getCacheObject(LOGIN_FAIL_IP_KEY + ip);
        return failCount != null && failCount >= maxLoginAttempts;
    }

    /**
     * 记录一次登录失败：递增失败计数并设置 TTL（锁定时长）。
     * <p>计数达到阈值即视为锁定，后续请求会被 {@link #isLocked(String)} 拦截。</p>
     *
     * @param username 用户名
     */
    public void recordLoginFailure(String username)
    {
        String key = LOGIN_FAIL_KEY + username;
        Integer currentFailCount = redisCache.getCacheObject(key);
        int newFailCount = (currentFailCount == null ? 0 : currentFailCount) + 1;
        redisCache.setCacheObject(key, newFailCount, lockTimeMinutes, TimeUnit.MINUTES);
    }

    /**
     * [C8] 记录一次 IP 维度登录失败：递增 IP 失败计数并设置<b>递增</b>锁定时长 TTL。
     *
     * @param ip 客户端 IP（为 {@code null}/空时直接返回，不做记录）
     */
    public void recordIpLoginFailure(String ip)
    {
        if (ip == null || ip.isBlank())
        {
            return;
        }
        String key = LOGIN_FAIL_IP_KEY + ip;
        Integer currentFailCount = redisCache.getCacheObject(key);
        int newFailCount = (currentFailCount == null ? 0 : currentFailCount) + 1;
        // 递增锁定时长：失败越多次，锁定越久（封顶 8 倍基础时长）
        int durationMinutes = computeLockMinutes(newFailCount);
        redisCache.setCacheObject(key, newFailCount, durationMinutes, TimeUnit.MINUTES);
    }

    /**
     * 计算 IP 维度锁定时长：基础时长 * (1 + 已失败次数/阈值)，封顶 8 倍。
     *
     * @param failCount 当前累计失败次数（含本次）
     * @return 锁定时长（分钟）
     */
    private int computeLockMinutes(int failCount)
    {
        int multiplier = 1 + (failCount / Math.max(1, maxLoginAttempts));
        return lockTimeMinutes * Math.min(multiplier, 8);
    }

    /**
     * 清除指定账号的失败计数（登录成功时调用）。
     *
     * @param username 用户名
     */
    public void clearFailures(String username)
    {
        redisCache.deleteObject(LOGIN_FAIL_KEY + username);
    }

    /**
     * [C8] 清除指定 IP 的失败计数（登录成功时调用）。
     *
     * @param ip 客户端 IP（为 {@code null}/空时直接返回）
     */
    public void clearIpFailures(String ip)
    {
        if (ip == null || ip.isBlank())
        {
            return;
        }
        redisCache.deleteObject(LOGIN_FAIL_IP_KEY + ip);
    }

    /**
     * 返回该账号还可以失败几次。
     * <p>已锁定（失败次数达到阈值）时返回 0。</p>
     *
     * @param username 用户名
     * @return 剩余可失败次数（非负）
     */
    public int getRemainingAttempts(String username)
    {
        if (isLocked(username))
        {
            return 0;
        }
        Integer failCount = redisCache.getCacheObject(LOGIN_FAIL_KEY + username);
        int used = (failCount == null ? 0 : failCount);
        return Math.max(0, maxLoginAttempts - used);
    }

    /**
     * 获取锁定时长（分钟），供文案展示使用。
     *
     * @return 锁定分钟数
     */
    public int getLockMinutes()
    {
        return lockTimeMinutes;
    }
}
