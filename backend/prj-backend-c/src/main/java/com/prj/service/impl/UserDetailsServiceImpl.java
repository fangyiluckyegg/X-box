package com.prj.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.prj.common.core.domain.entity.User;
import com.prj.common.core.domain.model.LoginUser;
import com.prj.common.exception.ServiceException;
import com.prj.service.IUserService;

/**
 * Spring Security 用户详情服务实现类。
 *
 * <p>职责：
 * 实现 Spring Security 的 {@link UserDetailsService}，在登录认证流程中
 * 根据用户名加载用户，并将其包装为 {@link LoginUser}（UserDetails 实现），
 * 供 AuthenticationProvider 进行密码校验与授权。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code IUserService}（按用户名查用户）、{@code User}/{@code LoginUser} 实体、
 *         {@code ServiceException}。
 * - 被依赖：Spring Security 认证管理器（{@code DaoAuthenticationProvider}）。
 *
 * <p>说明：用户不存在时抛出 {@link ServiceException}（业务异常），由全局异常处理器转换为统一响应。
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService
{
    /** 类级日志对象。 */
    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    /** 用户业务服务，由 Spring 自动注入。 */
    @Autowired
    private IUserService userService;

    /**
     * 根据用户名加载用户详情（Spring Security 认证回调）。
     *
     * @param username 登录用户名
     * @return 包装后的 UserDetails（LoginUser）
     * @throws UsernameNotFoundException 当用户不存在时（此处以 ServiceException 包装抛出）
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException
    {
        User user = userService.selectUserByUserName(username);
        if (user == null)
        {
            log.info("用户：{} 不存在.", username);
            throw new ServiceException("用户：" + username + " 不存在");
        }

        return createLoginUser(user);
    }

    /**
     * 将领域用户实体包装为 Spring Security 所需的 LoginUser。
     *
     * @param user 用户实体
     * @return LoginUser（同时持有 userId 与 User）
     */
    public UserDetails createLoginUser(User user)
    {
        return new LoginUser(user.getUserId(), user);
    }
}
