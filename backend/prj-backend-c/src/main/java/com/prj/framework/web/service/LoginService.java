package com.prj.framework.web.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
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
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private RedisCache redisCache;

    // [P1-S9] 锁定逻辑抽取到 AccountLockService，统一失败计数与防用户枚举
    @Autowired
    private AccountLockService accountLockService;

    //验证用户身份
    public String login(String username, String password, String code, String uuid)
    {
        // [P1-S9] 暴力破解防护：检查账号是否被锁定
        if (accountLockService.isLocked(username))
        {
            throw new ServiceException("账号已锁定，请 " + accountLockService.getLockMinutes() + " 分钟后重试");
        }

        try
        {
            // [P1-S9-2] 验证码校验与用户认证纳入同一 try：
            // 验证码失败同样计入失败次数（堵住"用验证码失败无限猜密码"的绕过），但不泄露用户名存在性。
            validateCaptcha(username, code, uuid);

            // 该方法会去调用UserDetailsServiceImpl.loadUserByUsername
            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));

            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            // [P1-S9] 登录成功，清除失败计数
            accountLockService.clearFailures(username);
            // 生成token
            return tokenService.createToken(loginUser);
        }
        catch (CaptchaException e)
        {
            // [P1-S9-2] 验证码错误：计入失败次数后原样抛出（类型不变，避免影响既有 P1-4 校验）
            accountLockService.recordLoginFailure(username);
            throw e;
        }
        catch (CaptchaExpireException e)
        {
            // 验证码失效：同样计入失败次数后原样抛出
            accountLockService.recordLoginFailure(username);
            throw e;
        }
        catch (Exception e)
        {
            // [P1-S9-1] 防用户枚举：用户不存在（loadUserByUsername 抛 ServiceException）与凭据错误
            // （BadCredentialsException）统一归一化为 UserPasswordNotMatchException，避免侧信道暴露用户名是否存在。
            accountLockService.recordLoginFailure(username);
            throw new UserPasswordNotMatchException();
        }
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
