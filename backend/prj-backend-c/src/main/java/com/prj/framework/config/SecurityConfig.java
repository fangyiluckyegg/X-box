package com.prj.framework.config;

import com.prj.framework.security.filter.JwtAuthenticationTokenFilter;
import com.prj.framework.security.handle.LogoutSuccessHandlerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.filter.CorsFilter;

/**
 * Spring Security 主安全配置。
 *
 * <p>职责：
 * 声明无状态（JWT）安全体系——
 * - 禁用 CSRF（令牌走 Bearer Header，非 Cookie）；
 * - 会话策略为 STATELESS；
 * - 配置请求授权：仅 {@code /login}、{@code /captchaImage}、静态资源、swagger/druid（部分）等白名单 permitAll，其余均需认证；
 * - 注册 {@link JwtAuthenticationTokenFilter}（在用户名密码过滤器前）与全局 {@code CorsFilter}；
 * - 暴露 {@link AuthenticationManager} 与 {@link BCryptPasswordEncoder} Bean。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code JwtAuthenticationTokenFilter}、{@code LogoutSuccessHandlerImpl}、{@code UserDetailsServiceImpl}（userDetailsService）、{@code CorsFilter}（来自 ResourcesConfig）。
 * - 被依赖：Spring Security 框架在启动时装配整个过滤器链；配合 {@code @PreAuthorize} 方法级鉴权（@EnableMethodSecurity）。
 */
// [P0-FIX] @EnableMethodSecurity（Spring Security 5.6+ / 6 推荐方式，功能等价旧 @EnableGlobalMethodSecurity）
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig
{
    //定义用户认证方法
    @Autowired
    private UserDetailsService userDetailsService;

    //退出登录的处理类
    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    //token认证过滤器
    @Autowired
    private JwtAuthenticationTokenFilter authenticationTokenFilter;

    //跨域过滤器
    @Autowired
    private CorsFilter corsFilter;

    // [P1-UPGRADE] Security 6：AuthenticationManager 由 AuthenticationConfiguration 暴露，
    // 不再覆写已删除的 authenticationManagerBean()
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception
    {
        return config.getAuthenticationManager();
    }

    // [P1-UPGRADE] Security 6：移除 WebSecurityConfigurerAdapter，改为声明 SecurityFilterChain Bean
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception
    {
        // [C11] CSRF 当前禁用。本系统采用无状态鉴权：令牌通过 Authorization: Bearer <JWT> 请求头传递，
        // 而非 Cookie，因此传统 CSRF（依赖 Cookie 自动携带）风险较低。后续如需进一步加固，
        // 可恢复 CSRF Token 或维持 Bearer Header + SameSite 策略。
        httpSecurity
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // [P1-UPGRADE] authorizeRequests → authorizeHttpRequests，antMatchers → requestMatchers
            .authorizeHttpRequests(auth -> auth
                // [P0-FIX] 移除 /api/** permitAll，所有业务接口必须经过认证
                // 白名单（匿名/permitAll）务必包含，防止全站 403
                .requestMatchers("/login", "/captchaImage").permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/*.html",
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js",
                        "/profile/**"
                ).permitAll()
                // [P1-FIX] springdoc-openapi 路径匹配（替代 Springfox 的 /swagger-ui.html 和 /swagger-resources/**）
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/webjars/**").permitAll()
                .requestMatchers("/doc.html").permitAll()
                // [C11/C13] Druid 监控控制台：要求 ADMIN 角色（双保险，配合 LoginUser.getAuthorities 的 ROLE_ADMIN 映射）。
                // 必须存在至少一个 role='ADMIN' 的真实用户（见 init.sql / migrate_role.sql）才能访问。
                .requestMatchers("/druid/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        httpSecurity.logout(logout -> logout.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler));
        // [P1-UPGRADE] 在 UsernamePasswordAuthenticationFilter 前插入 JWT 过滤器；其前插入 CORS 过滤器
        httpSecurity.addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        httpSecurity.addFilterBefore(corsFilter, JwtAuthenticationTokenFilter.class);
        httpSecurity.addFilterBefore(corsFilter, LogoutFilter.class);

        return httpSecurity.build();
    }

    //加密密码
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}
