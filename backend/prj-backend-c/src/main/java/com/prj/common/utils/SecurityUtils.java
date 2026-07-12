package com.prj.common.utils;

import com.prj.common.core.domain.model.LoginUser;
import com.prj.common.exception.ServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


/**
 * 安全上下文工具类。
 *
 * <p>职责：
 * 提供从 Spring Security 当前安全上下文（{@code SecurityContextHolder}）中获取
 * 当前登录用户（{@link LoginUser}）、其用户名与底层 {@code Authentication} 的便捷静态方法。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code LoginUser}（当前用户模型）、{@code ServiceException}（获取失败时抛出）。
 * - 被依赖：{@code CompareController}（取当前用户名）、{@code LoginController}、各需鉴权信息的业务处。
 *
 * <p>说明：若上下文中无认证信息（如未登录或被清理），统一抛出 code=401 的 {@link ServiceException}。
 */
public class SecurityUtils
{
    /** 获取用户账户（用户名）    **/
    public static String getUsername()
    {
        try
        {
            return getLoginUser().getUsername();
        }
        catch (Exception e)
        {
            throw new ServiceException("获取用户账户异常", 401);
        }
    }

    /** 获取当前登录用户（LoginUser）    **/
    public static LoginUser getLoginUser()
    {
        try
        {
            return (LoginUser) getAuthentication().getPrincipal();
        }
        catch (Exception e)
        {
            throw new ServiceException("获取用户信息异常", 401);
        }
    }

    /** 获取当前 Authentication 对象（含认证主体与权限）。 */
    public static Authentication getAuthentication()
    {
        return SecurityContextHolder.getContext().getAuthentication();
    }

}
