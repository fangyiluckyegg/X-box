package com.prj.verification;

import com.google.code.kaptcha.Producer;
import com.prj.common.core.redis.RedisCache;
import com.prj.controller.CaptchaController;
import com.prj.controller.LoginController;
import com.prj.framework.security.filter.JwtAuthenticationTokenFilter;
import com.prj.framework.security.handle.LogoutSuccessHandlerImpl;
import com.prj.framework.web.service.LoginService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.CorsFilter;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-W4 白名单 / 静态资源访问矩阵。
 * <p>
 * 采用 @SpringBootTest(webEnvironment = MOCK) + @AutoConfigureMockMvc + @MockBean 打桩，
 * 完整加载 Spring Security 过滤链（@WebMvcTest 切片不会把 springSecurityFilterChain 挂到 MockMvc），
 * 精确断言匿名访问白名单端点拿到 200/404 而非 500（核心验证 Boot3 PathPatternParser 升级后无 500）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class WhitelistAccessTest
{
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private LogoutSuccessHandlerImpl logoutSuccessHandler;
    @MockBean(name = "captchaProducerMath")
    private Producer captchaProducerMath;
    @MockBean
    private RedisCache redisCache;
    @MockBean
    private LoginService loginService;

    @Test
    @DisplayName("W1: GET /captchaImage 匿名应返回 200（无 500）")
    void w1_captchaImage_returns200() throws Exception
    {
        when(captchaProducerMath.createText()).thenReturn("3*5@15");
        when(captchaProducerMath.createImage(anyString()))
                .thenReturn(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
        when(loginService.login(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("fake-jwt-token");

        mockMvc.perform(get("/captchaImage"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("W2: POST /login 匿名应返回 200/400（登录链路兼容 Boot3），禁止 500")
    void w2_login_not500() throws Exception
    {
        when(loginService.login(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("fake-jwt-token");

        mockMvc.perform(post("/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"admin123\",\"code\":\"00000\",\"uuid\":\"any\"}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    assertTrue(s == 200 || s == 400,
                            "W2 期望 200/400（登录链路兼容 Boot3），实际 " + s);
                    assertTrue(s < 500, "W2 禁止 500（PathPatternParser 修复验证），实际 " + s);
                });
    }

    @Test
    @DisplayName("W3: GET / 匿名应返回 200/404 且非 500")
    void w3_root_not500() throws Exception
    {
        mockMvc.perform(get("/"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    assertTrue(s == 200 || s == 404, "W3 期望 200/404，实际 " + s);
                    assertTrue(s < 500, "W3 禁止 500，实际 " + s);
                });
    }

    @Test
    @DisplayName("W4: 静态资源 /*.html, /**/*.css, /**/*.js 匿名应 200/404 且非 500")
    void w4_staticResources_not500() throws Exception
    {
        String[] paths = {"/index.html", "/login.html", "/css/style.css", "/js/app.js", "/profile/avatar.png"};
        for (String p : paths)
        {
            mockMvc.perform(get(p))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        assertTrue(s == 200 || s == 404,
                                "W4 " + p + " 期望 200/404，实际 " + s);
                        assertTrue(s < 500, "W4 " + p + " 禁止 500，实际 " + s);
                    });
        }
    }
}
