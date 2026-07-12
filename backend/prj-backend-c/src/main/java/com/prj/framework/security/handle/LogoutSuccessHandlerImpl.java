package com.prj.framework.security.handle;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.prj.framework.web.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import com.prj.common.core.domain.model.LoginUser;


/**
 * 登出成功处理器。
 *
 * <p>职责：
 * 实现 Spring Security 的 {@code LogoutSuccessHandler}，在用户登出时：
 * 1) 通过 {@code TokenService} 删除 Redis 中的登录态缓存；
 * 2) 向前端返回统一 JSON 响应（{@code {"code":200,"msg":"退出成功"}}），与前端响应拦截器对齐。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code TokenService}（删除登录态）。
 * - 被依赖：{@code SecurityConfig}（配置为 logout 成功处理器）。
 */
@Configuration
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler
{
    private static final Logger logger = LoggerFactory.getLogger(LogoutSuccessHandlerImpl.class);

    @Autowired
    private TokenService tokenService;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (loginUser != null)
        {
            // 删除用户缓存记录
            tokenService.delLoginUser(loginUser.getToken());
        }

        response.setStatus(200);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        try {
            // [P1-24-FIX] 返回 JSON 格式，与前端响应拦截器一致，避免 code=undefined
            response.getWriter().print("{\"code\":200,\"msg\":\"退出成功\"}");
        } catch (IOException e) {
            // [P1-16-FIX] 替换 printStackTrace 为结构化日志
            logger.error("退出登录处理异常", e);
        }
    }
}
