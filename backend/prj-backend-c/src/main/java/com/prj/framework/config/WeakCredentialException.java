package com.prj.framework.config;

/**
 * 启动期弱凭证异常。
 * <p>
 * 当<b>严格模式</b>（prod 或无 active profile）下检测到默认/弱凭证（JWT 密钥、数据库口令、Redis 口令、Druid 口令）
 * 时由 {@link StartupSecurityValidator} 抛出，用于 fail-fast 阻止应用启动。
 * </p>
 * <p>
 * 设计上本应继承 {@link RuntimeException}；此处选择继承 {@link IllegalStateException}，
 * 以向后兼容既有单元测试 {@code StartupValidatorTest} 中
 * {@code assertThrows(IllegalStateException.class, ...)} 的断言（测试文件不在本任务修改范围内）。
 * 由于 {@link IllegalStateException} 本身即为 {@link RuntimeException} 的子类，语义与预期一致。
 * </p>
 */
public class WeakCredentialException extends IllegalStateException
{
    public WeakCredentialException(String message)
    {
        super(message);
    }
}
