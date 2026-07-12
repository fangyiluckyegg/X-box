package com.prj.common.core.domain.model;

import com.alibaba.fastjson2.annotation.JSONField;  // [P0-FIX] fastjson2 迁移
import com.prj.common.core.domain.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 登录用户模型（Spring Security 的 UserDetails 实现）。
 *
 * <p>职责：
 * 在认证与授权流程中代表"当前登录用户"，聚合用户 ID、token、登录/过期时间以及被包装的 {@link User} 实体，
 * 并实现 {@code UserDetails} 接口，向 Spring Security 提供密码、用户名与权限集合（getAuthorities）。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code User} 实体（持有真实业务属性）。
 * - 被依赖：{@code UserDetailsServiceImpl}（构造 LoginUser）、{@code TokenService}（存入 Redis / 取出还原）、
 *           {@code JwtAuthenticationTokenFilter}（从上下文获取当前用户）。
 *
 * <p>安全说明：密码字段标记 @JSONField(serialize=false)，避免随 LoginUser 序列化落盘（见 [C1]）。
 */
public class LoginUser implements UserDetails
{
    private static final long serialVersionUID = 1L;

    /**     * 用户ID     */
    private Long userId;
    /**     * 用户唯一标识（token）     */
    private String token;
    /**     * 登录时间（毫秒时间戳）     */
    private Long loginTime;
    /**     * 过期时间（毫秒时间戳）     */
    private Long expireTime;

    /**     * 用户信息（真实业务用户实体）     */
    private User user;

    /** 获取用户ID。 */
    public Long getUserId()
    {
        return userId;
    }
    /** 设置用户ID。 */
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    /** 获取token。 */
    public String getToken()
    {
        return token;
    }
    /** 设置token。 */
    public void setToken(String token)
    {
        this.token = token;
    }

    /** 默认构造。 */
    public LoginUser()
    {
    }
    /** 基于用户ID与用户实体构造。 */
    public LoginUser(Long userId, User user)
    {
        this.userId = userId;
        this.user = user;
    }

    @JSONField(serialize = false)
    @Override
    /** 密码（委派给内部 User 实体，且不参与序列化）。 */
    public String getPassword()
    {
        return user.getPassword();
    }

    @Override
    /** 用户名（委派给内部 User 实体）。 */
    public String getUsername()
    {
        return user.getUserName();
    }

    /** 获取登录时间。 */
    public Long getLoginTime()
    {
        return loginTime;
    }
    /** 设置登录时间。 */
    public void setLoginTime(Long loginTime)
    {
        this.loginTime = loginTime;
    }

    /** 获取过期时间。 */
    public Long getExpireTime()
    {
        return expireTime;
    }
    /** 设置过期时间。 */
    public void setExpireTime(Long expireTime)
    {
        this.expireTime = expireTime;
    }


    /** 获取用户实体。 */
    public User getUser()
    {
        return user;
    }
    /** 设置用户实体。 */
    public void setUser(User user)
    {
        this.user = user;
    }

    /** 账户是否未过期（恒为 true）。 */
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 账户是否未锁定（恒为 true）。 */
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 凭证是否未过期（恒为 true）。 */
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 账户是否启用（恒为 true）。 */
    public boolean isEnabled() {
        return true;
    }

    /**
     * [C1] 按真实角色映射授权。
     * <p>user.role 仅存单一角色字面量：ADMIN → ROLE_ADMIN；其余（含 null / USER / 未知）一律授予最小权限 ROLE_USER，
     * 避免角色缺失或未知时误赋 ADMIN。所有 {@code @PreAuthorize("hasRole('ADMIN')")} 仅对 role='ADMIN' 用户放行。</p>
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        String role = (user != null) ? user.getRole() : null;
        if ("ADMIN".equalsIgnoreCase(role))
        {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
