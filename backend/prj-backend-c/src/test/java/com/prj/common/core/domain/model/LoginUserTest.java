package com.prj.common.core.domain.model;

import com.prj.common.core.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LoginUser.getAuthorities() 单元测试（纯 JUnit 5，不加载 Spring 上下文）。
 * <p>
 * 钉死 C1 权限改动的核心角色映射逻辑，避免回归测试/P1-4 接口测试（@WithMockUser 或 mock authenticationManager）
 * 绕过真实映射导致逻辑裸奔：
 * <ul>
 *   <li>role="ADMIN"（忽略大小写）→ 仅 [ROLE_ADMIN]</li>
 *   <li>其余（含 null / "USER" / 任意未知字符串）→ 仅 [ROLE_USER]（失败关闭，绝不误赋 ADMIN）</li>
 * </ul>
 * </p>
 */
class LoginUserTest
{
    private static final Long SAMPLE_USER_ID = 1L;

    /** 提取 authority 集合为字符串集合，便于断言 */
    private static Set<String> authorityStrings(LoginUser loginUser)
    {
        Set<String> result = new HashSet<>();
        for (GrantedAuthority ga : loginUser.getAuthorities())
        {
            result.add(ga.getAuthority());
        }
        return result;
    }

    private static LoginUser loginUserWithRole(String role)
    {
        User user = new User();
        user.setUserName("tester");
        user.setPassword("unused");
        user.setRole(role);
        return new LoginUser(SAMPLE_USER_ID, user);
    }

    @Test
    @DisplayName("role='ADMIN' → 仅含 ROLE_ADMIN，不含 ROLE_USER")
    void roleAdmin_grantsOnlyAdmin()
    {
        LoginUser loginUser = loginUserWithRole("ADMIN");

        Set<String> authorities = authorityStrings(loginUser);

        assertEquals(1, authorities.size(), "ADMIN 应只返回单一授权");
        assertTrue(authorities.contains("ROLE_ADMIN"), "应包含 ROLE_ADMIN");
        assertFalse(authorities.contains("ROLE_USER"), "ADMIN 不应误含 ROLE_USER");
    }

    @Test
    @DisplayName("role='admin'（小写）→ 仍含 ROLE_ADMIN（大小写不敏感）")
    void roleAdminLowerCase_grantsAdmin()
    {
        LoginUser loginUser = loginUserWithRole("admin");

        Set<String> authorities = authorityStrings(loginUser);

        assertTrue(authorities.contains("ROLE_ADMIN"), "小写 admin 也应映射为 ROLE_ADMIN");
        assertFalse(authorities.contains("ROLE_USER"), "小写 admin 不应降级为 ROLE_USER");
    }

    @Test
    @DisplayName("role='USER' → 含 ROLE_USER")
    void roleUser_grantsUser()
    {
        LoginUser loginUser = loginUserWithRole("USER");

        Set<String> authorities = authorityStrings(loginUser);

        assertEquals(1, authorities.size(), "USER 应只返回单一授权");
        assertTrue(authorities.contains("ROLE_USER"), "应包含 ROLE_USER");
        assertFalse(authorities.contains("ROLE_ADMIN"), "USER 不应获得 ROLE_ADMIN");
    }

    @Test
    @DisplayName("role=null → 含 ROLE_USER（失败关闭，不误赋 ADMIN）")
    void roleNull_grantsUserByFailClosed()
    {
        LoginUser loginUser = loginUserWithRole(null);

        Set<String> authorities = authorityStrings(loginUser);

        assertEquals(1, authorities.size(), "角色缺失应只返回单一授权");
        assertTrue(authorities.contains("ROLE_USER"), "角色缺失应失败关闭授予最小权限 ROLE_USER");
        assertFalse(authorities.contains("ROLE_ADMIN"), "角色缺失绝不能误赋 ADMIN");
    }

    @Test
    @DisplayName("role='UNKNOWN_XYZ'（未知值）→ 含 ROLE_USER（失败关闭，不误赋 ADMIN）")
    void roleUnknown_grantsUserByFailClosed()
    {
        LoginUser loginUser = loginUserWithRole("UNKNOWN_XYZ");

        Set<String> authorities = authorityStrings(loginUser);

        assertEquals(1, authorities.size(), "未知角色应只返回单一授权");
        assertTrue(authorities.contains("ROLE_USER"), "未知角色应失败关闭授予最小权限 ROLE_USER");
        assertFalse(authorities.contains("ROLE_ADMIN"), "未知角色绝不能误赋 ADMIN");
    }
}
