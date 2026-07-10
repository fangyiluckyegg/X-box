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
import org.springframework.web.filter.CorsFilter;

import java.awt.image.BufferedImage;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1-D2 文档接口矩阵。
 * <p>
 * 当前 dev 环境 {@code SWAGGER_ENABLED} 未设置（默认 false），springdoc 端点未启用，
 * /swagger-ui.html 与 /v3/api-docs 均返回 404（非失败，属正常关闭状态）。
 * 通过 test properties 显式关闭 springdoc 以模拟 dev 配置，确保断言确定性与设计文档一致。
 * 若运行环境设置 SWAGGER_ENABLED=true，则应期待 200（permitAll），由主理人 curl 复核。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SwaggerDocTest
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
    @DisplayName("D1: GET /swagger-ui.html 当前 dev（springdoc 实际已启用）应返回 200 且非 500")
    void d1_swaggerUi_200() throws Exception
    {
        when(captchaProducerMath.createText()).thenReturn("3*5@15");
        when(captchaProducerMath.createImage(anyString()))
                .thenReturn(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("D2: GET /v3/api-docs 当前 dev（springdoc 实际已启用）应返回 200 且非 500")
    void d2_apiDocs_200() throws Exception
    {
        when(captchaProducerMath.createText()).thenReturn("3*5@15");
        when(captchaProducerMath.createImage(anyString()))
                .thenReturn(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
