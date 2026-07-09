package com.prj.framework.web.service;

import jakarta.annotation.Resource;

//import com.prj.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.prj.common.constant.Constants;
import com.prj.common.core.domain.model.LoginUser;
import com.prj.common.core.redis.RedisCache;
import com.prj.common.exception.ServiceException;
import com.prj.common.exception.user.CaptchaException;
import com.prj.common.exception.user.CaptchaExpireException;
import com.prj.common.exception.user.UserPasswordNotMatchException;

@Component
public class LoginService
{
    // [P1-FIX] 暴力破解防护：5 次失败后锁定 15 分钟
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 15;
    private static final String LOGIN_FAIL_KEY = "login_fail:";

    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private RedisCache redisCache;

    // @Autowired
    //private IUserService userService;

    //验证用户身份
    public String login(String username, String password, String code, String uuid)
    {
        // [P1-FIX] 暴力破解防护：检查账号是否被锁定
        String lockKey = LOGIN_FAIL_KEY + username;
        Integer failCount = redisCache.getCacheObject(lockKey);
        if (failCount != null && failCount >= MAX_LOGIN_ATTEMPTS)
        {
            throw new ServiceException("账号已锁定，请 " + LOCK_TIME_MINUTES + " 分钟后重试");
        }

        validateCaptcha(username, code, uuid);

        // 用户验证
        Authentication authentication = null;
        try
        {
            // 该方法会去调用UserDetailsServiceImpl.loadUserByUsername
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
        }
        catch (Exception e)
        {
            if (e instanceof BadCredentialsException)
            {
                // [P1-FIX] 递增失败计数，超过阈值锁定账号
                Integer currentFailCount = redisCache.getCacheObject(lockKey);
                int newFailCount = (currentFailCount == null ? 0 : currentFailCount) + 1;
                redisCache.setCacheObject(lockKey, newFailCount, LOCK_TIME_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
                throw new UserPasswordNotMatchException();
            }
            else
            {
                throw new ServiceException(e.getMessage());
            }
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        // [P1-FIX] 登录成功，清除失败计数
        redisCache.deleteObject(LOGIN_FAIL_KEY + username);
        // 生成token
        return tokenService.createToken(loginUser);
    }

    // 检查验证码
    public void validateCaptcha(String username, String code, String uuid)
    {
        String verifyKey = Constants.CAPTCHA_CODE_KEY + uuid;
        String captcha = redisCache.getCacheObject(verifyKey);
        redisCache.deleteObject(verifyKey);
        if (captcha == null)
        {
            throw new CaptchaExpireException();
        }
        // [P1-FIX] 先检查 code 非空，防止 NPE
        if (code == null || !code.equalsIgnoreCase(captcha))
        {
            throw new CaptchaException();
        }
    }

}
