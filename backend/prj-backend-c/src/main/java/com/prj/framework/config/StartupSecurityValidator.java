package com.prj.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动期密钥/口令安全校验器。
 * <p>
 * 在应用启动阶段对关键凭证做 fail-fast 校验，避免弱默认值被带上生产环境。
 * <ul>
 *   <li>宽松环境（{@code dev}/{@code test}/{@code local}）：命中弱默认值仅打印醒目 WARN 日志，不阻止启动。</li>
 *   <li>严格环境（{@code prod} 或无任何 active profile）：命中弱默认值或 JWT 密钥长度不足，
 *       抛出 {@link WeakCredentialException} 阻止应用启动。</li>
 * </ul>
 * </p>
 * <p>
 * 弱值集合与 JWT 最小字节数均可通过配置覆盖：
 * <ul>
 *   <li>{@code security.weak-credential-values}：逗号分隔的弱值列表，为空时回退到内置默认集合。</li>
 *   <li>{@code security.jwt-secret-min-bytes}：JWT 密钥最小字节数（默认 32，对应 HS256 至少 256 位）。</li>
 * </ul>
 * </p>
 */
@Component
public class StartupSecurityValidator implements ApplicationRunner
{
    private static final Logger logger = LoggerFactory.getLogger(StartupSecurityValidator.class);

    // 内置默认弱值集合（与 application.yml 中的 :default 保持一致）
    private static final Set<String> DEFAULT_WEAK_VALUES = Set.of(
            "please-set-a-strong-secret-key-at-least-256-bits",
            "Prj@Dev789",
            "redis_default_pass_change_me",
            "Druid@Dev2024"
    );

    // 宽松（仅告警不阻止启动）环境集合
    private static final Set<String> RELAXED_PROFILES = Set.of("dev", "test", "local");

    private final Environment environment;
    private final String jwtSecret;
    private final String dbPassword;
    private final String redisPassword;
    private final String druidPassword;

    // 以下两项支持配置化；由 Spring 注入构造器赋值，单测通过 5 参构造器回退到默认值。
    private final String weakCredentialValues;
    private final int jwtSecretMinBytes;

    /**
     * 兼容既有单元测试（StartupValidatorTest）的 5 参构造器。
     * 弱值列表与 JWT 最小字节数回退到内置默认值。
     */
    public StartupSecurityValidator(Environment environment,
            String jwtSecret,
            String dbPassword,
            String redisPassword,
            String druidPassword)
    {
        this(environment, jwtSecret, dbPassword, redisPassword, druidPassword, "", 32);
    }

    /**
     * Spring 注入构造器：支持配置化弱值集合与 JWT 最小字节数。
     */
    @Autowired
    public StartupSecurityValidator(Environment environment,
            @Value("${token.secret}") String jwtSecret,
            @Value("${spring.datasource.password}") String dbPassword,
            // [P1-UPGRADE] Spring Boot 3 / Spring Data Redis 3：spring.redis.password → spring.data.redis.password
            @Value("${spring.data.redis.password}") String redisPassword,
            @Value("${spring.datasource.druid.stat-view-servlet.login-password}") String druidPassword,
            @Value("${security.weak-credential-values:}") String weakCredentialValues,
            @Value("${security.jwt-secret-min-bytes:32}") int jwtSecretMinBytes)
    {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
        this.redisPassword = redisPassword;
        this.druidPassword = druidPassword;
        this.weakCredentialValues = weakCredentialValues;
        this.jwtSecretMinBytes = jwtSecretMinBytes;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        // 严格模式：没有任何 active profile 落在 {dev,test,local} 中（prod 或无 active profile 均视为严格）
        boolean strict = activeProfiles.stream().noneMatch(RELAXED_PROFILES::contains);

        Set<String> weakValues = resolveWeakValues();

        List<String> weakItems = new ArrayList<>();
        checkWeak("JWT_SECRET", jwtSecret, weakValues, weakItems);
        checkWeak("SPRING_DATASOURCE_PASSWORD", dbPassword, weakValues, weakItems);
        checkWeak("REDIS_PASSWORD", redisPassword, weakValues, weakItems);
        checkWeak("DRUID_PASSWORD", druidPassword, weakValues, weakItems);

        // JWT 密钥长度硬性要求（HS256 至少 256 位 = 32 字节，可通过配置覆盖）
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < jwtSecretMinBytes)
        {
            weakItems.add("JWT_SECRET(长度不足" + jwtSecretMinBytes + "字节)");
        }

        if (weakItems.isEmpty())
        {
            logger.info("[Security] 关键凭证校验通过");
            return;
        }

        if (!strict)
        {
            logger.warn("[Security][RELAXED] 检测到使用默认/弱凭证（仅 {} 环境允许）: {}。"
                    + " 生产环境必须通过环境变量覆盖这些凭证！", activeProfiles, weakItems);
            return;
        }

        // 严格模式（prod / 无 active profile）：阻止启动
        throw new WeakCredentialException("生产环境禁止使用默认/弱凭证，请在环境变量中覆盖: " + weakItems);
    }

    /**
     * 解析配置化的弱值集合；为空或空白时回退到内置默认集合。
     */
    private Set<String> resolveWeakValues()
    {
        if (weakCredentialValues == null || weakCredentialValues.isBlank())
        {
            return DEFAULT_WEAK_VALUES;
        }
        return Arrays.stream(weakCredentialValues.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 判断单个凭证是否为空或命中弱值集合，命中则计入 weakItems。 */
    private void checkWeak(String name, String value, Set<String> weakValues, List<String> weakItems)
    {
        if (value == null || value.isBlank() || weakValues.contains(value))
        {
            weakItems.add(name);
        }
    }
}
