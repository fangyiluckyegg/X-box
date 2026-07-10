package com.prj.verification;

import com.google.code.kaptcha.Producer;
import com.prj.common.core.redis.RedisCache;
import com.prj.controller.CaptchaController;
import com.prj.controller.CompareController;
import com.prj.controller.EmployeeKpiController;
import com.prj.controller.LoginController;
import com.prj.controller.PositionLearningController;
import com.prj.framework.security.filter.JwtAuthenticationTokenFilter;
import com.prj.framework.security.handle.LogoutSuccessHandlerImpl;
import com.prj.framework.web.service.LoginService;
import com.prj.service.IEmployeeKpiService;
import com.prj.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CorsFilter;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * B1-B9 业务接口匿名访问矩阵。
 * <p>
 * 核心判据：匿名请求必须被安全链拒绝（401/403）且<b>绝对不能 500</b>，
 * 这直接验证 Boot2→3 的 PathPatternParser 严格化修复生效（旧实现曾因 ant 表达式在 3.x 下抛 500）。
 * 同时覆盖 B8 /error 不回显堆栈/SQL/绝对路径（S10）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BusinessEndpointDeniedTest
{
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup()
    {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

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
    @MockBean
    private IEmployeeKpiService employeeKpiService;
    @MockBean
    private UploadService uploadService;

    /** 断言匿名业务接口被拒绝（401/403）且非 5xx。 */
    private static void assertDeniedAndNot500(int status, String matrixItem)
    {
        assertTrue(status == 401 || status == 403,
                matrixItem + " 匿名期望 401/403（拒绝且非500），实际 " + status);
        assertTrue(status < 500, matrixItem + " 禁止 500（PathPatternParser 修复），实际 " + status);
    }

    @Test
    @DisplayName("B1: GET /api/excel/progress 匿名必须 401/403 且非 500")
    void b1_progress() throws Exception
    {
        mockMvc.perform(get("/api/excel/progress"))
                .andExpect(r -> assertDeniedAndNot500(r.getResponse().getStatus(), "B1"));
    }

    @Test
    @DisplayName("B2: POST /api/excel/compare 匿名必须 401/403 且非 500")
    void b2_compare() throws Exception
    {
        mockMvc.perform(multipart("/api/excel/compare"))
                .andExpect(r -> assertDeniedAndNot500(r.getResponse().getStatus(), "B2"));
    }

    @Test
    @DisplayName("B3: GET /api/excel/downloadResult 匿名必须 401/403 且非 500")
    void b3_downloadResult() throws Exception
    {
        mockMvc.perform(get("/api/excel/downloadResult"))
                .andExpect(r -> assertDeniedAndNot500(r.getResponse().getStatus(), "B3"));
    }

    @Test
    @DisplayName("B4: GET /employee_kpi/list 匿名必须 401/403 且非 500")
    void b4_kpiList() throws Exception
    {
        mockMvc.perform(get("/employee_kpi/list"))
                .andExpect(r -> assertDeniedAndNot500(r.getResponse().getStatus(), "B4"));
    }

    @Test
    @DisplayName("B5: GET /employee_kpi/1 匿名必须 401/403 且非 500")
    void b5_kpiDetail() throws Exception
    {
        mockMvc.perform(get("/employee_kpi/1"))
                .andExpect(r -> assertDeniedAndNot500(r.getResponse().getStatus(), "B5"));
    }

    @Test
    @DisplayName("B6: GET /api/positionLearning/download 匿名必须 401/403 且非 500")
    void b6_positionDownload() throws Exception
    {
        mockMvc.perform(get("/api/positionLearning/download"))
                .andExpect(r -> assertDeniedAndNot500(r.getResponse().getStatus(), "B6"));
    }

    @Test
    @DisplayName("B7: POST /api/positionLearning/docUpload/uploadDoc 匿名必须 401/403 且非 500")
    void b7_positionUpload() throws Exception
    {
        mockMvc.perform(multipart("/api/positionLearning/docUpload/uploadDoc"))
                .andExpect(r -> assertDeniedAndNot500(r.getResponse().getStatus(), "B7"));
    }

    @Test
    @DisplayName("B8/S10: GET /error 匿名必须 401/403，且响应体不得回显堆栈/SQL/绝对路径")
    void b8_errorNoLeak() throws Exception
    {
        mockMvc.perform(get("/error"))
                .andExpect(r -> {
                    int status = r.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "B8 GET /error 匿名期望 401/403，实际 " + status);
                    assertTrue(status < 500, "B8 禁止 500，实际 " + status);
                    assertNoSensitiveLeak(r.getResponse().getContentAsString());
                });
    }

    @Test
    @DisplayName("B9: POST /logout 匿名返回 200/302（LogoutFilter 对匿名登出同样处理，非 500，非安全绕过）")
    void b9_logoutAnonymous() throws Exception
    {
        mockMvc.perform(post("/logout"))
                .andExpect(r -> {
                    int s = r.getResponse().getStatus();
                    assertTrue(s == 200 || s == 302,
                            "B9 匿名登出期望 200/302（LogoutFilter 处理，非 500），实际 " + s);
                    assertTrue(s < 500, "B9 禁止 500，实际 " + s);
                });
    }

    @Test
    @DisplayName("B9: POST /logout 已认证（ADMIN）应返回 200（登出成功）")
    @WithMockUser(roles = "ADMIN")
    void b9_logoutAuthenticated() throws Exception
    {
        mockMvc.perform(post("/logout"))
                .andExpect(r -> assertTrue(r.getResponse().getStatus() == 200,
                        "B9 已认证登出期望 200，实际 " + r.getResponse().getStatus()));
    }

    /** S10：断言响应体不含堆栈/SQL/绝对路径等敏感信息。 */
    private static void assertNoSensitiveLeak(String body)
    {
        String lower = (body == null ? "" : body).toLowerCase();
        String[] leaks = {
                "exception", "caused by", "at com.prj", "java.lang",
                "select ", "insert ", "update ", "delete ",
                "stacktrace", "at org.springframework",
                "/workspace", "c:\\", "d:\\"
        };
        for (String leak : leaks)
        {
            assertFalse(lower.contains(leak),
                    "响应体疑似泄露敏感信息，命中关键字: [" + leak + "] | body=" + body);
        }
    }
}
