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
        // [P1-FIX] CSRF 当前禁用，因 Token 存储在 Cookie 中存在 CSRF 风险。
        // 已通过前端 SameSite=Lax Cookie 缓解。后续应评估恢复 CSRF Token 或改为 Authorization Header 方案。
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
                // [P0-FIX] Druid控制台要求ADMIN角色认证，移除anonymous
                .requestMatchers("/druid/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(h -> h.frameOptions(frameOptions -> frameOptions.disable()));

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
