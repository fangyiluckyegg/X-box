package com.prj.framework.config;

import com.prj.framework.security.filter.JwtAuthenticationTokenFilter;
import com.prj.framework.security.handle.LogoutSuccessHandlerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.filter.CorsFilter;

// [P0-FIX] @EnableGlobalMethodSecurity → @EnableMethodSecurity（Spring Security 5.6+ 推荐方式，功能等价）
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter
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

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception
    {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception
    {
        // [P1-FIX] CSRF 当前禁用，因 Token 存储在 Cookie 中存在 CSRF 风险。
        // 已通过前端 SameSite=Lax Cookie 缓解。后续应评估恢复 CSRF Token 或改为 Authorization Header 方案。
        httpSecurity
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeRequests(auth -> auth
                // [P0-FIX] 移除 /api/** permitAll，所有业务接口必须经过认证
                .antMatchers("/login", "/captchaImage").anonymous()
                .antMatchers(
                        HttpMethod.GET,
                        "/",
                        "/*.html",
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js",
                        "/profile/**"
                ).permitAll()
                // [P1-FIX] springdoc-openapi 路径匹配（替代 Springfox 的 /swagger-ui.html 和 /swagger-resources/**）
                .antMatchers("/swagger-ui/**").anonymous()
                .antMatchers("/v3/api-docs/**").anonymous()
                .antMatchers("/webjars/**").anonymous()
                .antMatchers("/doc.html").anonymous()
                // [P0-FIX] Druid控制台要求ADMIN角色认证，移除anonymous
                .antMatchers("/druid/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions().disable());

        httpSecurity.logout(logout -> logout.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler));
        httpSecurity.addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        httpSecurity.addFilterBefore(corsFilter, JwtAuthenticationTokenFilter.class);
        httpSecurity.addFilterBefore(corsFilter, LogoutFilter.class);
    }

    //加密密码
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    //身份认证接口
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception
    {
        //该注释用来解密登录所用的密码
        auth.userDetailsService(userDetailsService).passwordEncoder(bCryptPasswordEncoder());
    }
}
