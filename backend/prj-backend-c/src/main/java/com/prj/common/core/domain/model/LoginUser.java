package com.prj.common.core.domain.model;

import com.alibaba.fastjson2.annotation.JSONField;  // [P0-FIX] fastjson2 迁移
import com.prj.common.core.domain.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class LoginUser implements UserDetails
{
    private static final long serialVersionUID = 1L;

    /**     * 用户ID     */
    private Long userId;
    /**     * 用户唯一标识     */
    private String token;
    /**     * 登录时间     */
    private Long loginTime;
    /**     * 过期时间     */
    private Long expireTime;

    /**     * 用户信息     */
    private User user;

    public Long getUserId()
    {
        return userId;
    }
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getToken()
    {
        return token;
    }
    public void setToken(String token)
    {
        this.token = token;
    }

    public LoginUser()
    {
    }
    public LoginUser(Long userId, User user)
    {
        this.userId = userId;
        this.user = user;
    }

    @JSONField(serialize = false)
    @Override
    public String getPassword()
    {
        return user.getPassword();
    }

    @Override
    public String getUsername()
    {
        return user.getUserName();
    }

    public Long getLoginTime()
    {
        return loginTime;
    }
    public void setLoginTime(Long loginTime)
    {
        this.loginTime = loginTime;
    }

    public Long getExpireTime()
    {
        return expireTime;
    }
    public void setExpireTime(Long expireTime)
    {
        this.expireTime = expireTime;
    }


    public User getUser()
    {
        return user;
    }
    public void setUser(User user)
    {
        this.user = user;
    }

    public boolean isAccountNonExpired() {
        return true;
    }

    public boolean isAccountNonLocked() {
        return true;
    }

    public boolean isCredentialsNonExpired() {
        return true;
    }

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
