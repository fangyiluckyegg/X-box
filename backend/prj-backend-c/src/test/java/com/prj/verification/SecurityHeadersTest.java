package com.prj.verification;

import com.google.code.kaptcha.Producer;
import com.prj.common.core.redis.RedisCache;
import com.prj.controller.CaptchaController;
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

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S6 CORS 限制矩阵（使用真实的 ResourcesConfig.CorsFilter，非 mock）。
 * <p>
 * 验证：携带非法 Origin（http://evil.com）请求白名单端点时，响应头<b>不存在</b>
 * {@code Access-Control-Allow-Origin: *}，且非法源不被回显；
 * 携带合法白名单源（http://127.0.0.1:8081）时，回显该源且绝非通配符。
 * 允许源仅来自 application.properties 的 cors.allowed-origins。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityHeadersTest
{
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private LogoutSuccessHandlerImpl logoutSuccessHandler;
    // 注意：此处不 @MockBean CorsFilter，使用 ResourcesConfig 注入的真实跨域过滤器
    @MockBean(name = "captchaProducerMath")
    private Producer captchaProducerMath;
    @MockBean
    private RedisCache redisCache;
    @MockBean
    private LoginService loginService;

    @Test
    @DisplayName("S6: 非法 Origin http://evil.com 请求白名单端点，响应头不得出现 Access-Control-Allow-Origin: * 且不得回显该源")
    void s6_illegalOrigin_noWildcard() throws Exception
    {
        when(captchaProducerMath.createText()).thenReturn("3*5@15");
        when(captchaProducerMath.createImage(anyString()))
                .thenReturn(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(get("/captchaImage").header("Origin", "http://evil.com"))
                .andExpect(r -> {
                    int s = r.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 200 || s == 403,
                            "S6 非法源状态期望 200/403，实际 " + s);
                })
                .andExpect(r -> {
                    String acao = r.getResponse().getHeader("Access-Control-Allow-Origin");
                    assertNotEquals("*", acao,
                            "S6 失败：响应头出现通配符 Access-Control-Allow-Origin: *");
                    assertNotEquals("http://evil.com", acao,
                            "S6 失败：非法 Origin 被回显为 Access-Control-Allow-Origin");
                });
    }

    @Test
    @DisplayName("S6: 合法白名单 Origin http://127.0.0.1:8081 请求白名单端点，应回显该源且绝非通配符")
    void s6_legalOrigin_echoedNoWildcard() throws Exception
    {
        when(captchaProducerMath.createText()).thenReturn("3*5@15");
        when(captchaProducerMath.createImage(anyString()))
                .thenReturn(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(get("/captchaImage").header("Origin", "http://127.0.0.1:8081"))
                .andExpect(status().isOk())
                .andExpect(r -> {
                    String acao = r.getResponse().getHeader("Access-Control-Allow-Origin");
                    assertNotNull(acao, "S6 失败：合法源应回显 Access-Control-Allow-Origin");
                    assertNotEquals("*", acao,
                            "S6 失败：合法源响应头不应为通配符 *");
                    org.junit.jupiter.api.Assertions.assertEquals("http://127.0.0.1:8081", acao,
                            "S6 失败：应精确回显白名单源，实际 " + acao);
                });
    }
}
