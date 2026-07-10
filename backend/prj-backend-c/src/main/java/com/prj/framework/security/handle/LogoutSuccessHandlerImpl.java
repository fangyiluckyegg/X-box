package com.prj.framework.security.handle;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.prj.framework.web.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import com.prj.common.core.domain.model.LoginUser;


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
