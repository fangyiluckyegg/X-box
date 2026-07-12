package com.prj.common.exception.user;

/**
 * 用户名与密码不匹配异常。
 *
 * <p>职责：
 * 表示登录时密码校验未通过，属于 {@link UserException} 的子类。
 *
 * <p>与其他模块的关联：
 * - 被依赖：登录流程（{@code LoginService}）在密码比对失败时抛出。
 */
public class UserPasswordNotMatchException extends UserException
{
    /** 默认提示"密码不匹配"。 */
    public UserPasswordNotMatchException()
    {
        super("密码不匹配");
    }
}
