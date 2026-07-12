package com.prj.controller;

import com.prj.common.constant.Constants;
import com.prj.common.core.domain.AjaxResult;
import com.prj.common.core.domain.model.LoginBody;
import com.prj.common.utils.IpUtils;
import com.prj.framework.web.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * 用户登录接口。
     *
     * @param loginBody 登录请求体（@Valid 触发 LoginBody 上 JSR-303 校验：用户名/密码/验证码非空等）
     * @param request   HTTP 请求（用于解析客户端真实 IP）
     * @return 成功响应，data 中携带 {@code token} 字段；校验失败由 LoginService 抛出业务异常
     */
    @PostMapping("/login")
    // [P0-FIX] @Valid 触发 LoginBody 上的 JSR-303 约束校验
    public AjaxResult login(@Valid @RequestBody LoginBody loginBody, HttpServletRequest request)
    {
        AjaxResult ajax = AjaxResult.success();
        // [C8/C12] 解析真实客户端 IP 并透传至登录锁定逻辑
        String clientIp = IpUtils.getClientIp(request);
        // 完成登录，生成token
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid(), clientIp);
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

}
