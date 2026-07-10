package com.prj.framework.web.service;

import com.prj.common.constant.Constants;
import com.prj.common.core.domain.model.LoginUser;
import com.prj.common.core.redis.RedisCache;
import com.prj.common.exception.ServiceException;
import com.prj.common.exception.user.CaptchaException;
import com.prj.common.exception.user.CaptchaExpireException;
import com.prj.common.exception.user.UserPasswordNotMatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginService 单元测试（纯 Mockito，不加载 Spring 上下文）。
 * <p>
 * 验证 S9 加固点：
 * <ul>
 *   <li>验证码失败（CaptchaException / CaptchaExpireException）同样计入失败次数（S9-2），异常原样抛出（类型不变）。</li>
 *   <li>用户不存在 / 凭据错误统一归一化为 {@link UserPasswordNotMatchException}（S9-1 防用户枚举），
 *       无论底层异常为何，都先 {@code recordLoginFailure}。</li>
 *   <li>已锁定账号走 isLocked 早返回，不调用 authenticate，直接抛 {@link ServiceException}("账号已锁定...")。</li>
 *   <li>正常登录成功清除失败计数并返回 token。</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest
{
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin123";
    private static final String CODE = "1234";
    private static final String UUID = "test-uuid-001";

    @Mock
    private TokenService tokenService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private RedisCache redisCache;
    @Mock
    private AccountLockService accountLockService;

    @InjectMocks
    private LoginService loginService;

    @BeforeEach
    void setUp()
    {
        // 默认：账号未锁定。
        // 验证码 stub 标记为 lenient：alreadyLocked 用例在 isLocked 早返回、不会走到 validateCaptcha，
        // 该 stub 不会被执行，严格 stubbing 会报 UnnecessaryStubbingException，故放宽。
        when(accountLockService.isLocked(USERNAME)).thenReturn(false);
        lenient().when(redisCache.getCacheObject(Constants.CAPTCHA_CODE_KEY + UUID)).thenReturn(CODE);
    }

    @Test
    @DisplayName("用户不存在（authenticate 抛异常）：仍记录失败次数，并归一化为 UserPasswordNotMatchException（防枚举）")
    void userNotExist_recordsFailureAndThrowsNotMatch()
    {
        // 模拟 loadUserByUsername 抛 ServiceException（用户不存在），被 authenticationManager.authenticate 透传
        when(authenticationManager.authenticate(any())).thenThrow(new ServiceException("用户不存在"));

        UserPasswordNotMatchException ex = assertThrows(UserPasswordNotMatchException.class,
                () -> loginService.login(USERNAME, PASSWORD, CODE, UUID));

        verify(accountLockService).recordLoginFailure(USERNAME);
        verify(authenticationManager).authenticate(any());
    }

    @Test
    @DisplayName("验证码错误（CaptchaException）：仍记录失败次数，并原样抛出 CaptchaException（S9-2）")
    void captchaError_recordsFailureAndThrowsCaptchaException()
    {
        // 让验证码校验失败：redis 返回与传入 code 不一致的值
        when(redisCache.getCacheObject(Constants.CAPTCHA_CODE_KEY + UUID)).thenReturn("9999");

        CaptchaException ex = assertThrows(CaptchaException.class,
                () -> loginService.login(USERNAME, PASSWORD, CODE, UUID));

        verify(accountLockService).recordLoginFailure(USERNAME);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("验证码失效（CaptchaExpireException）：仍记录失败次数，并原样抛出（S9-2）")
    void captchaExpire_recordsFailureAndThrowsCaptchaExpireException()
    {
        when(redisCache.getCacheObject(Constants.CAPTCHA_CODE_KEY + UUID)).thenReturn(null);

        CaptchaExpireException ex = assertThrows(CaptchaExpireException.class,
                () -> loginService.login(USERNAME, PASSWORD, CODE, UUID));

        verify(accountLockService).recordLoginFailure(USERNAME);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("账号已锁定：不调用认证，直接抛 ServiceException('账号已锁定...')")
    void alreadyLocked_throwsServiceExceptionWithoutAuthenticate()
    {
        when(accountLockService.isLocked(USERNAME)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> loginService.login(USERNAME, PASSWORD, CODE, UUID));

        verify(authenticationManager, never()).authenticate(any());
        verify(accountLockService, never()).recordLoginFailure(any());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("账号已锁定"));
    }

    @Test
    @DisplayName("正常登录：清除失败计数并返回 token")
    void normalLogin_clearsFailuresAndReturnsToken()
    {
        Authentication authentication = mock(Authentication.class);
        LoginUser loginUser = mock(LoginUser.class);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenService.createToken(loginUser)).thenReturn("fake-jwt-token");

        String token = loginService.login(USERNAME, PASSWORD, CODE, UUID);

        assertEquals("fake-jwt-token", token);
        verify(accountLockService).clearFailures(USERNAME);
        verify(tokenService).createToken(loginUser);
    }
}
