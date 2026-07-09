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
