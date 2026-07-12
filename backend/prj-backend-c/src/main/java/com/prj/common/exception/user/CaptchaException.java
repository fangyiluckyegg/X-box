package com.prj.common.exception.user;

/**
 * 验证码错误异常。
 *
 * <p>职责：
 * 表示用户提交的验证码与缓存中的答案不匹配，属于用户相关异常 {@link UserException} 的子类。
 *
 * <p>与其他模块的关联：
 * - 被依赖：登录流程（{@code LoginService}）在比对验证码不符时抛出，最终由全局异常处理器转换为统一响应。
 */
public class CaptchaException extends UserException
{
    private static final long serialVersionUID = 1L;

    /** 默认提示"验证码错误"。 */
    public CaptchaException()
    {
        super("验证码错误");
    }
}
