package com.prj.verification;

import com.prj.framework.config.StartupSecurityValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1-4 启动期弱凭证/密钥校验器矩阵（纯单元测试，无 Spring 容器依赖，快速稳定）。
 * <p>
 * - dev profile 命中弱默认值：仅 WARN，不抛异常（不阻止启动）。
 * - 非 dev（如 prod）profile 命中弱默认值：抛 {@link IllegalStateException} 阻止启动。
 * 四个受检凭证：JWT_SECRET / SPRING_DATASOURCE_PASSWORD / REDIS_PASSWORD / DRUID_PASSWORD，
 * 与 StartupSecurityValidator.WEAK_VALUES 默认值一致。
 */
class StartupValidatorTest
{
    // 与 StartupSecurityValidator.WEAK_VALUES 一致的默认弱值
    private static final String WEAK_JWT = "please-set-a-strong-secret-key-at-least-256-bits";
    private static final String WEAK_DB = "Prj@Dev789";
    private static final String WEAK_REDIS = "redis_default_pass_change_me";
    private static final String WEAK_DRUID = "Druid@Dev2024";

    private static StartupSecurityValidator buildValidator(String[] activeProfiles,
                                                           String jwt, String db, String redis, String druid)
    {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(activeProfiles);
        return new StartupSecurityValidator(env, jwt, db, redis, druid);
    }

    @Test
    @DisplayName("P1-4: dev profile 使用默认弱凭证，StartupSecurityValidator 仅 WARN，不抛异常（不阻止启动）")
    void p14_devProfile_weakCredentials_warnsOnly()
    {
        StartupSecurityValidator validator =
                buildValidator(new String[]{"dev"}, WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);

        assertDoesNotThrow(() -> validator.run(mock(ApplicationArguments.class)),
                "P1-4 失败：dev profile 下校验器不应阻止启动（仅 WARN）");
    }

    @Test
    @DisplayName("P1-4: 非 dev（prod）profile 使用默认弱凭证，StartupSecurityValidator 应抛 IllegalStateException 阻止启动")
    void p14_prodProfile_weakCredentials_throws()
    {
        StartupSecurityValidator validator =
                buildValidator(new String[]{"prod"}, WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);

        assertThrows(IllegalStateException.class, () -> validator.run(mock(ApplicationArguments.class)),
                "P1-4 失败：prod profile 下校验器应抛 IllegalStateException 阻止启动");
    }

    @Test
    @DisplayName("P1-4: dev profile 使用强凭证，StartupSecurityValidator 不抛异常且通过校验")
    void p14_devProfile_strongCredentials_ok()
    {
        StartupSecurityValidator validator = buildValidator(
                new String[]{"dev"},
                "a-very-strong-32-byte-minimum-jwt-secret-key-xyz1234567890",
                "Very$tr0ng@DbP@ssw0rd!",
                "Very$tr0ng@R3d1sP@ss!",
                "Very$tr0ng@Dr11dP@ss!");

        assertDoesNotThrow(() -> validator.run(mock(ApplicationArguments.class)),
                "P1-4 失败：dev profile 强凭证应通过校验且不阻止启动");
    }
}
