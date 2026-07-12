package com.prj.framework.security.filter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.prj.framework.web.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.prj.common.core.domain.model.LoginUser;
import com.prj.common.utils.SecurityUtils;

/**
 * JWT 认证过滤器（无状态鉴权核心）。
 *
 * <p>职责：
 * 作为 Spring Security 过滤器链的一环（{@code OncePerRequestFilter}，每个请求仅执行一次），
 * 从请求中解析并校验 JWT，还原出 {@link LoginUser} 后写入 {@code SecurityContextHolder}，
 * 使后续 {@code @PreAuthorize}、{@code SecurityUtils.getLoginUser()} 等可获取当前用户。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code TokenService}（解析/校验 token 与登录态）、{@code SecurityUtils}（取当前认证信息）。
 * - 被依赖：{@code SecurityConfig}（注册到 UsernamePasswordAuthenticationFilter 之前）。
 *
 * <p>流程：仅当请求携带有效 loginUser 且当前上下文尚无认证信息时，才校验并写入，
 * token 校验失败仅告警并放行（由后续授权拦截器处理 401）。
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter
{
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    @Autowired
    private TokenService tokenService;

@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        // [P1-FIX] 替换 System.out.println 为 SLF4J logger，避免泄露 loginUser 对象
        logger.debug("JwtAuthenticationTokenFilter loginUser: {}", loginUser != null ? loginUser.getUsername() : "null");
        if (loginUser != null && SecurityUtils.getAuthentication() == null)
        {
            try {
                tokenService.verifyToken(loginUser);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (Exception e) {
                // [P1-FIX] 替换 System.out.println 为 SLF4J logger
                logger.warn("Token verify failed: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
