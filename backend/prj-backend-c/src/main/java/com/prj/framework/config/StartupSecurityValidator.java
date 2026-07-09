package com.prj.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动期密钥/口令安全校验器。
 * <p>
 * 在应用启动阶段对关键凭证做 fail-fast 校验，避免弱默认值被带上生产环境。
 * <ul>
 *   <li>dev profile：命中弱默认值仅打印醒目 WARN 日志，不阻止启动（保持开发便捷）。</li>
 *   <li>非 dev profile（prod/test）：命中弱默认值或 JWT 密钥长度不足 256 位（32 字节，HS256 最低要求），
 *       抛出 {@link IllegalStateException} 阻止应用启动。</li>
 * </ul>
 * </p>
 */
@Component
public class StartupSecurityValidator implements ApplicationRunner
{
    private static final Logger logger = LoggerFactory.getLogger(StartupSecurityValidator.class);

    // 已知的弱默认值集合（与 application.yml 中的 :default 保持一致）
    private static final java.util.Set<String> WEAK_VALUES = java.util.Set.of(
            "please-set-a-strong-secret-key-at-least-256-bits",
            "Prj@Dev789",
            "redis_default_pass_change_me",
            "Druid@Dev2024"
    );

    private final Environment environment;
    private final String jwtSecret;
    private final String dbPassword;
    private final String redisPassword;
    private final String druidPassword;

    public StartupSecurityValidator(Environment environment,
            @Value("${token.secret}") String jwtSecret,
            @Value("${spring.datasource.password}") String dbPassword,
            // [P1-UPGRADE] Spring Boot 3 / Spring Data Redis 3：spring.redis.password → spring.data.redis.password
            @Value("${spring.data.redis.password}") String redisPassword,
            @Value("${spring.datasource.druid.stat-view-servlet.login-password}") String druidPassword)
    {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
        this.redisPassword = redisPassword;
        this.druidPassword = druidPassword;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        boolean isDev = java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev");

        List<String> weakItems = new ArrayList<>();
        checkWeak("JWT_SECRET", jwtSecret, weakItems);
        checkWeak("SPRING_DATASOURCE_PASSWORD", dbPassword, weakItems);
        checkWeak("REDIS_PASSWORD", redisPassword, weakItems);
        checkWeak("DRUID_PASSWORD", druidPassword, weakItems);

        // JWT 密钥长度硬性要求（HS256 至少 256 位 = 32 字节）
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)
        {
            weakItems.add("JWT_SECRET(长度不足32字节/256位)");
        }

        if (weakItems.isEmpty())
        {
            logger.info("[Security] 关键凭证校验通过");
            return;
        }

        if (isDev)
        {
            logger.warn("[Security][DEV] 检测到使用默认/弱凭证（仅开发环境允许）: {}。"
                    + " 生产环境必须通过环境变量覆盖这些凭证！", weakItems);
            return;
        }

        // 非 dev 环境：直接阻止启动
        throw new IllegalStateException("生产环境禁止使用默认/弱凭证，请在环境变量中覆盖: " + weakItems);
    }

    private void checkWeak(String name, String value, List<String> weakItems)
    {
        if (value == null || value.isBlank() || WEAK_VALUES.contains(value))
        {
            weakItems.add(name);
        }
    }
}
