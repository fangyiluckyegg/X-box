package com.prj.common.exception.user;

/**
 * 验证码过期异常。
 *
 * <p>职责：
 * 表示验证码已超出 Redis 中设置的过期时间，属于 {@link UserException} 的子类。
 *
 * <p>与其他模块的关联：
 * - 被依赖：登录流程（{@code LoginService}）在验证码不存在/过期时抛出。
 */
public class CaptchaExpireException extends UserException
{
    private static final long serialVersionUID = 1L;

    /** 默认提示"验证码已失效"。 */
    public CaptchaExpireException()
    {
        super("验证码已失效");
    }
}
