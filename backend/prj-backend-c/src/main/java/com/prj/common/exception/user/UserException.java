package com.prj.common.exception.user;


/**
 * 用户相关异常基类（运行时异常）。
 *
 * <p>职责：
 * 作为登录/用户认证相关异常的父类（如验证码错误、密码不匹配等），统一归类便于异常处理与提示。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code CaptchaException}、{@code CaptchaExpireException}、{@code UserPasswordNotMatchException} 继承本类；
 *           登录流程中抛出，由 {@code GlobalExceptionHandler} 兜底转换。
 */
/** 用户信息异常类 */
public class UserException extends RuntimeException
{
    /** 携带提示信息构造。 */
    public UserException(String msg)
    {
        super(msg);
    }
}
