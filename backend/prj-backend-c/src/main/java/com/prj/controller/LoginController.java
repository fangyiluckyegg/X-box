package com.prj.controller;

import com.prj.common.constant.Constants;
import com.prj.common.core.domain.AjaxResult;
import com.prj.common.core.domain.model.LoginBody;
import com.prj.common.utils.IpUtils;
import com.prj.framework.web.service.LoginService;
import com.prj.framework.web.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;


/**
 * 登录控制器（Login）。
 *
 * <p>职责：
 * 对外提供 {@code POST /login} 登录入口，接收前端提交的账号、密码、验证码与 uuid，
 * 将登录校验与令牌签发逻辑委托给 {@link LoginService}，最终把生成的 JWT token 返回前端。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code LoginService}（登录业务，负责凭证校验、验证码比对、账号锁定与 token 生成）、
 *         {@code LoginBody}（登录请求体模型，承载 username/password/code/uuid）、
 *         {@code IpUtils}（解析真实客户端 IP 并透传至登录锁定逻辑）、
 *         {@code Constants}（token 响应字段名）。
 * - 被依赖：前端登录页（web/prj-frontend/src/views/login.vue）调用该接口完成登录。
 *
 * <p>安全说明：写操作/凭证校验相关风险点已在各 [P0-FIX]/[C8/C12] 备注中标注（输入校验、真实 IP 透传等）。
 */
// [P0-FIX] 添加 @Validated 开启控制器级别输入校验
@Validated
@RestController
public class LoginController
{
    /** 登录业务服务，由 Spring 自动注入。 */
    @Autowired
    private LoginService loginService;

    // [F-11] 登录 Cookie 有效期（分钟），与 token.expireTime 对齐
    @Value("${token.expireTime:30}")
    private int tokenExpireMinutes;

    /**
     * 用户登录接口。
     *
     * @param loginBody 登录请求体（@Valid 触发 LoginBody 上 JSR-303 校验：用户名/密码/验证码非空等）
     * @param request   HTTP 请求（用于解析客户端真实 IP 与协议）
     * @param response  HTTP 响应（用于种 HttpOnly Cookie）
     * @return 成功响应，data 中携带 {@code token} 字段；校验失败由 LoginService 抛出业务异常
     */
    @PostMapping("/login")
    // [P0-FIX] @Valid 触发 LoginBody 上的 JSR-303 约束校验
    public AjaxResult login(@Valid @RequestBody LoginBody loginBody, HttpServletRequest request, HttpServletResponse response)
    {
        AjaxResult ajax = AjaxResult.success();
        // [C8/C12] 解析真实客户端 IP 并透传至登录锁定逻辑
        String clientIp = IpUtils.getClientIp(request);
        // 完成登录，生成token
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid(), clientIp);
        ajax.put(Constants.TOKEN, token);

        // [F-11-FIX] 以 HttpOnly + Secure + SameSite=Strict 种 Cookie；JS 无法读取，防 XSS 窃取令牌。
        // secure 依据原始请求协议（兼容前置 Nginx 的 X-Forwarded-Proto），本地 HTTP 开发亦可正常使用。
        boolean secure = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) || request.isSecure();
        ResponseCookie cookie = ResponseCookie.from(TokenService.ADMIN_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(tokenExpireMinutes * 60L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ajax;
    }

}
