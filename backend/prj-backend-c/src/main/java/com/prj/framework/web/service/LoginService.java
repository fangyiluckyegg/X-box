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

/**
 * 登录业务服务。
 *
 * <p>职责：
 * 编排完整登录认证流程——验证码校验、账号/IP 维度锁定判定、Spring Security 凭据认证、
 * 成功清锁并签发 JWT token。所有安全相关判定（防暴力破解、防用户枚举）均在此收口。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code TokenService}（签发/校验 token）、{@code AccountLockService}（失败计数与锁定）、
 *         {@code AuthenticationManager}（凭据认证，回调 UserDetailsServiceImpl）、{@code RedisCache}（取验证码）、{@code Constants}。
 * - 被依赖：{@code LoginController}（调用 login 完成登录）。
 *
 * <p>安全说明：验证码失败与密码错误统一计入失败次数并归一化为 UserPasswordNotMatchException，避免用户名枚举；锁定按用户名+IP 双维度，见 [C8]/[P1-S9] 备注。
 */
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
    /** 兼容旧签名：无客户端 IP 时仅按用户名维度锁定（委托 5 参版本，IP 传 null）。 */
    public String login(String username, String password, String code, String uuid)
    {
        // [C8/C12] 兼容旧签名：无客户端 IP 时仅按用户名维度锁定
        return login(username, password, code, uuid, null);
    }

    /**
     * [C8/C12] 登录认证（含客户端 IP）。
     * <p>登录失败/锁定判定同时按 <b>username</b> 与 <b>clientIp</b> 两个维度进行，
     * 任一维度被锁定即拒绝登录；登录成功时两个维度计数同时清除。</p>
     *
     * @param username  用户名
     * @param password  密码
     * @param code      验证码
     * @param uuid      验证码 uuid
     * @param clientIp  真实客户端 IP（由 {@code IpUtils.getClientIp} 解析；可为 {@code null}）
     * @return JWT token
     */
    public String login(String username, String password, String code, String uuid, String clientIp)
    {
        // [P1-S9] 暴力破解防护：账号维度锁定判定
        if (accountLockService.isLocked(username))
        {
            throw new ServiceException("账号已锁定，请 " + accountLockService.getLockMinutes() + " 分钟后重试");
        }
        // [C8] IP 维度锁定判定
        if (accountLockService.isIpLocked(clientIp))
        {
            throw new ServiceException("登录过于频繁，该IP已被锁定，请 " + accountLockService.getLockMinutes() + " 分钟后重试");
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
            // [P1-S9] 登录成功，清除失败计数（用户名 + IP 维度）
            accountLockService.clearFailures(username);
            accountLockService.clearIpFailures(clientIp);
            // 生成token
            return tokenService.createToken(loginUser);
        }
        catch (CaptchaException e)
        {
            // [P1-S9-2] 验证码错误：计入失败次数后原样抛出（类型不变，避免影响既有 P1-4 校验）
            recordFailure(username, clientIp);
            throw e;
        }
        catch (CaptchaExpireException e)
        {
            // 验证码失效：同样计入失败次数后原样抛出
            recordFailure(username, clientIp);
            throw e;
        }
        catch (Exception e)
        {
            // [P1-S9-1] 防用户枚举：用户不存在（loadUserByUsername 抛 ServiceException）与凭据错误
            // （BadCredentialsException）统一归一化为 UserPasswordNotMatchException，避免侧信道暴露用户名是否存在。
            recordFailure(username, clientIp);
            throw new UserPasswordNotMatchException();
        }
    }

    /**
     * [C8] 记录登录失败（用户名 + IP 维度）。
     */
    private void recordFailure(String username, String clientIp)
    {
        accountLockService.recordLoginFailure(username);
        accountLockService.recordIpLoginFailure(clientIp);
    }

    // 检查验证码
    /** 校验验证码：比对 Redis 中缓存的答案与用户提交值，校验后删除缓存（一次性）。 */
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
