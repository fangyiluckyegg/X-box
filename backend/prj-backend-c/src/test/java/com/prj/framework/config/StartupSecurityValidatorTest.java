package com.prj.framework.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StartupSecurityValidator 单元测试（纯 Mockito，无 Spring 上下文）。
 * <p>
 * 覆盖 S8 放宽 + S9 严格模式矩阵：
 * <ul>
 *   <li>宽松 profile（dev / test / local）命中弱值 → 仅 WARN 不抛（保护 P1-4 既有 33 接口测试）。</li>
 *   <li>严格 profile（prod / 无 active profile）命中弱值 → 抛 {@link WeakCredentialException}
 *       （也是 {@link IllegalStateException}，兼容既有 {@code StartupValidatorTest} 的断言）。</li>
 *   <li>JWT 长度不足 → 严格抛、宽松不抛。</li>
 *   <li>{@code security.weak-credential-values} 为空 → 回退内置默认弱值集合。</li>
 *   <li>7 参构造器支持自定义弱值集合与 JWT 最小字节数。</li>
 * </ul>
 * 本类与既有 {@code com.prj.verification.StartupValidatorTest} 不同包、不同名，互不干扰。
 * </p>
 */
class StartupSecurityValidatorTest
{
    private static final String WEAK_JWT = "please-set-a-strong-secret-key-at-least-256-bits";
    private static final String WEAK_DB = "Prj@Dev789";
    private static final String WEAK_REDIS = "redis_default_pass_change_me";
    private static final String WEAK_DRUID = "Druid@Dev2024";

    private static final String STRONG_JWT = "a-very-strong-32-byte-minimum-jwt-secret-key-xyz1234567890";
    private static final String STRONG_DB = "Very$tr0ng@DbP@ssw0rd!";
    private static final String STRONG_REDIS = "Very$tr0ng@R3d1sP@ss!";
    private static final String STRONG_DRUID = "Very$tr0ng@Dr11dP@ss!";

    private static Environment envWith(String[] profiles)
    {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        return env;
    }

    private static ApplicationArguments args()
    {
        return mock(ApplicationArguments.class);
    }

    // ===== 宽松 profile：命中弱值不抛（保护 P1-4） =====

    @Test
    @DisplayName("dev profile + 弱默认值：仅 WARN，不抛异常")
    void dev_weakCredentials_doesNotThrow()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"dev"}), WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);
        assertDoesNotThrow(() -> v.run(args()));
    }

    @Test
    @DisplayName("test profile + 弱默认值：仅 WARN，不抛异常")
    void test_weakCredentials_doesNotThrow()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"test"}), WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);
        assertDoesNotThrow(() -> v.run(args()));
    }

    @Test
    @DisplayName("local profile + 弱默认值：仅 WARN，不抛异常")
    void local_weakCredentials_doesNotThrow()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"local"}), WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);
        assertDoesNotThrow(() -> v.run(args()));
    }

    // ===== 严格 profile：命中弱值抛 WeakCredentialException（也是 IllegalStateException） =====

    @Test
    @DisplayName("prod profile + 弱默认值：抛 WeakCredentialException（兼容既有 IllegalStateException 断言）")
    void prod_weakCredentials_throwsWeakCredentialException()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);
        WeakCredentialException ex = assertThrows(WeakCredentialException.class, () -> v.run(args()));
        assertTrue(ex instanceof IllegalStateException, "WeakCredentialException 应继承自 IllegalStateException");
    }

    @Test
    @DisplayName("无 active profile（严格）：弱默认值抛 WeakCredentialException")
    void noProfile_weakCredentials_throws()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{}), WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);
        assertThrows(WeakCredentialException.class, () -> v.run(args()));
    }

    @Test
    @DisplayName("prod profile + 弱默认值：assertThrows(IllegalStateException.class) 通过（向后兼容 P1-4 旧断言）")
    void prod_weakCredentials_illegalStateExceptionAssertPasses()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID);
        assertThrows(IllegalStateException.class, () -> v.run(args()));
    }

    // ===== 严格 profile + 强值：不抛 =====

    @Test
    @DisplayName("prod profile + 强凭证：不抛异常，校验通过")
    void prod_strongCredentials_doesNotThrow()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), STRONG_JWT, STRONG_DB, STRONG_REDIS, STRONG_DRUID);
        assertDoesNotThrow(() -> v.run(args()));
    }

    // ===== JWT 长度校验 =====

    @Test
    @DisplayName("prod profile + JWT 长度不足：抛 WeakCredentialException")
    void prod_shortJwt_throws()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), "short", STRONG_DB, STRONG_REDIS, STRONG_DRUID);
        assertThrows(WeakCredentialException.class, () -> v.run(args()));
    }

    @Test
    @DisplayName("dev profile + JWT 长度不足：宽松模式仅 WARN 不抛")
    void dev_shortJwt_doesNotThrow()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"dev"}), "short", STRONG_DB, STRONG_REDIS, STRONG_DRUID);
        assertDoesNotThrow(() -> v.run(args()));
    }

    // ===== 弱值配置化为空 → 回退默认 =====

    @Test
    @DisplayName("弱值配置为空：回退内置默认弱值，prod 下命中仍抛")
    void emptyWeakValuesConfig_fallsBackToDefaults()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), WEAK_JWT, WEAK_DB, WEAK_REDIS, WEAK_DRUID, "", 32);
        assertThrows(WeakCredentialException.class, () -> v.run(args()));
    }

    // ===== 7 参构造器：自定义弱值集合 =====

    @Test
    @DisplayName("自定义弱值集合命中：prod 下抛 WeakCredentialException")
    void customWeakValues_triggers()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), STRONG_JWT, "myCustomWeak", STRONG_REDIS, STRONG_DRUID,
                "myCustomWeak,anotherWeak", 32);
        assertThrows(WeakCredentialException.class, () -> v.run(args()));
    }

    @Test
    @DisplayName("自定义弱值集合未命中 + 强值：不抛")
    void customWeakValues_noMatch_doesNotThrow()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), STRONG_JWT, STRONG_DB, STRONG_REDIS, STRONG_DRUID,
                "myCustomWeak,anotherWeak", 32);
        assertDoesNotThrow(() -> v.run(args()));
    }

    // ===== 7 参构造器：JWT 最小字节数可配置 =====

    @Test
    @DisplayName("JWT 最小字节数降低后达标：prod 下不抛")
    void jwtMinBytesConfigurable_passes()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), "12345678", STRONG_DB, STRONG_REDIS, STRONG_DRUID,
                "", 8);
        assertDoesNotThrow(() -> v.run(args()));
    }

    @Test
    @DisplayName("JWT 最小字节数提高后不达标：prod 下抛")
    void jwtMinBytesConfigurable_fails()
    {
        StartupSecurityValidator v = new StartupSecurityValidator(
                envWith(new String[]{"prod"}), STRONG_JWT, STRONG_DB, STRONG_REDIS, STRONG_DRUID,
                "", 64);
        assertThrows(WeakCredentialException.class, () -> v.run(args()));
    }
}
