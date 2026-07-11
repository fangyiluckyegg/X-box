package com.prj.framework.web.service;

import com.prj.common.constant.Constants;
import com.prj.common.core.domain.entity.User;
import com.prj.common.core.domain.model.LoginUser;
import com.prj.common.core.redis.RedisCache;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TokenService JWT 鉴权安全单元测试（纯 Mockito，不加载 Spring 上下文）。
 *
 * <p>钉死“认证/授权”维度的核心契约（与审查报告第 5 节“认证/授权 验证为安全”呼应，并补齐自动化覆盖）：
 * 1) 合法 token 可被正确解析为用户（getLoginUser 返回非 null 且用户名一致）；
 * 2) 被篡改签名的 token（有效结构、错误签名）被拒绝（返回 null）；
 * 3) 非法/垃圾 token 被拒绝（返回 null）；
 * 4) 缺失/空 token 被拒绝（返回 null）；
 * 5) getUsernameFromToken 对篡改 token 返回 null（不抛异常、不泄露信息）。</p>
 *
 * <p>密钥通过 ReflectionTestUtils 注入一个 ≥32 字节的强密钥，模拟 prod 严格校验
 * （application-prod.yml 要求 JWT_SECRET ≥ 32 字节，否则 jjwt 0.12.x 的 Keys.hmacShaKeyFor 抛 WeakKeyException）。</p>
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceSecurityTest
{
    private static final String STRONG_SECRET = "this-is-a-strong-32byte-plus-secret-key-1234567890";

    @Mock
    private RedisCache redisCache;

    @InjectMocks
    private TokenService tokenService;

    private LoginUser sampleLoginUser()
    {
        User user = new User();
        user.setUserName("alice");
        user.setPassword("unused-bcrypt-hash");
        user.setRole("ADMIN");
        return new LoginUser(1L, user);
    }

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(tokenService, "header", "Authorization");
        ReflectionTestUtils.setField(tokenService, "secret", STRONG_SECRET);
        ReflectionTestUtils.setField(tokenService, "expireTime", 30);
        // 任意 userKey 都返回同一 LoginUser，模拟 Redis 命中
        when(redisCache.getCacheObject(anyString())).thenReturn(sampleLoginUser());
    }

    private HttpServletRequest mockRequestWithBearer(String token)
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        if (token != null)
        {
            when(request.getHeader("Authorization")).thenReturn(Constants.TOKEN_PREFIX + token);
        }
        else
        {
            when(request.getHeader("Authorization")).thenReturn(null);
        }
        return request;
    }

    @Test
    @DisplayName("合法 token：getLoginUser 返回非 null 且用户名一致")
    void validToken_resolvesToUser()
    {
        String token = tokenService.createToken(sampleLoginUser());
        assertNotNull(token);

        LoginUser loginUser = tokenService.getLoginUser(mockRequestWithBearer(token));

        assertNotNull(loginUser, "合法签名的 token 应解析出用户");
        assertEquals("alice", loginUser.getUsername());
    }

    @Test
    @DisplayName("篡改签名 token：getLoginUser 应拒绝（返回 null）")
    void tamperedToken_rejected()
    {
        String token = tokenService.createToken(sampleLoginUser());
        // 篡改签名段最后一个字符（payload 与签名之间以 '.' 分隔）
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        LoginUser loginUser = tokenService.getLoginUser(mockRequestWithBearer(tampered));

        assertNull(loginUser, "签名被篡改的 token 必须被拒绝（防伪造/重放）");
    }

    @Test
    @DisplayName("垃圾 token：getLoginUser 应拒绝（返回 null）")
    void garbageToken_rejected()
    {
        LoginUser loginUser = tokenService.getLoginUser(mockRequestWithBearer("not-a-real-jwt"));
        assertNull(loginUser, "非法 token 必须被拒绝");
    }

    @Test
    @DisplayName("缺失 token：getLoginUser 应拒绝（返回 null）")
    void missingToken_rejected()
    {
        LoginUser loginUser = tokenService.getLoginUser(mockRequestWithBearer(null));
        assertNull(loginUser, "无 Authorization 头时必须拒绝");
    }

    @Test
    @DisplayName("篡改 token：getUsernameFromToken 返回 null（不抛异常）")
    void tamperedToken_usernameNull()
    {
        String token = tokenService.createToken(sampleLoginUser());
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertNull(tokenService.getUsernameFromToken(tampered), "篡改 token 无法解析出用户名");
    }
}
