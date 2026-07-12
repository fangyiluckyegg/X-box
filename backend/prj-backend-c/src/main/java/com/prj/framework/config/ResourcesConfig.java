package com.prj.framework.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
//import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;


/**
 * Web 资源与跨域（CORS）配置。
 *
 * <p>职责：
 * 作为 {@code WebMvcConfigurer} 提供全局 CORS 过滤器 Bean——仅放行配置中列出的可信前端源
 * （不再使用通配符 {@code *}），允许携带凭证、任意头与方法，预检缓存 1800 秒。
 * 全局拦截 {@code /**} 路径统一应用该跨域策略。
 *
 * <p>与其他模块的关联：
 * - 被依赖：所有前后端跨域请求均经此过滤器；配合前端（web/prj-frontend）开发/生产地址。
 *
 * <p>安全说明：允许源由 {@code cors.allowed-origins} 配置注入，已补充无端口（默认 80）与 127.0.0.1 等变体，见 [P0-FIX]/[BUGFIX]。
 */
@Configuration
public class ResourcesConfig implements WebMvcConfigurer
{
    // [P0-FIX] CORS允许的源地址从配置读取，不再使用通配符"*"
    // [BUGFIX] 补充 127.0.0.1 和无端口(=80) 版本，浏览器对默认端口省略端口号
    @Value("${cors.allowed-origins:http://localhost:8080,http://localhost:80,http://localhost:8081,http://localhost,http://127.0.0.1:8080,http://127.0.0.1:80,http://127.0.0.1:8081,http://127.0.0.1}")
    private String[] allowedOrigins;

    /**
     * 跨域配置（CORS 过滤器 Bean）。
     */
    @Bean
    public CorsFilter corsFilter()
    {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // [P0-FIX] 从配置读取允许的源地址，限制跨域范围
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        // 设置访问源请求头
        config.addAllowedHeader("*");
        // 设置访问源请求方法
        config.addAllowedMethod("*");
        // 有效期 1800秒
        config.setMaxAge(1800L);
        // 添加映射路径，拦截一切请求
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        // 返回新的CorsFilter
        return new CorsFilter(source);
    }
}
