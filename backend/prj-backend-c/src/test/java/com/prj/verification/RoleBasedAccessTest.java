package com.prj.verification;

import com.prj.controller.EmployeeKpiController;
import com.prj.framework.security.filter.JwtAuthenticationTokenFilter;
import com.prj.framework.security.handle.LogoutSuccessHandlerImpl;
import com.prj.service.IEmployeeKpiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.CorsFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R1-R4 角色 / ADMIN 门槛矩阵。
 * <p>
 * 写操作（POST/PUT/DELETE /employee_kpi/**）受 @PreAuthorize("hasRole('ADMIN')") 保护；
 * /druid/** 受 SecurityConfig 的 hasRole("ADMIN") 保护。
 * 校验：匿名→401/403，非 ADMIN(USER)→403，ADMIN→放行（200 或 302/404，取决于 Druid 控制台是否在测试切片内）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoleBasedAccessTest
{
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private LogoutSuccessHandlerImpl logoutSuccessHandler;
    @MockBean
    private IEmployeeKpiService employeeKpiService;

    private static final String VALID_KPI_BODY = "{\"kpi\":\"A\",\"manager\":\"hr\"}";

    // ---------------- R1: POST /employee_kpi ----------------
    @Test
    @DisplayName("R1: POST /employee_kpi 匿名应 401/403")
    void r1_anonymous() throws Exception
    {
        mockMvc.perform(post("/employee_kpi").contentType("application/json").content(VALID_KPI_BODY))
                .andExpect(r -> {
                    int s = r.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403,
                            "匿名访问应被拒绝（401/403），实际 " + s);
                });
    }

    @Test
    @DisplayName("R1: POST /employee_kpi 非 ADMIN(USER) 应 403")
    @WithMockUser(roles = "USER")
    void r1_nonAdmin() throws Exception
    {
        mockMvc.perform(post("/employee_kpi").contentType("application/json").content(VALID_KPI_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R1: POST /employee_kpi ADMIN 应 200（写操作放行）")
    @WithMockUser(roles = "ADMIN")
    void r1_admin() throws Exception
    {
        when(employeeKpiService.insertEmployeeKpi(any())).thenReturn(1);
        mockMvc.perform(post("/employee_kpi").contentType("application/json").content(VALID_KPI_BODY))
                .andExpect(status().isOk());
    }

    // ---------------- R2: PUT /employee_kpi ----------------
    @Test
    @DisplayName("R2: PUT /employee_kpi 匿名应 401/403")
    void r2_anonymous() throws Exception
    {
        mockMvc.perform(put("/employee_kpi").contentType("application/json").content(VALID_KPI_BODY))
                .andExpect(r -> {
                    int s = r.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403,
                            "匿名访问应被拒绝（401/403），实际 " + s);
                });
    }

    @Test
    @DisplayName("R2: PUT /employee_kpi 非 ADMIN(USER) 应 403")
    @WithMockUser(roles = "USER")
    void r2_nonAdmin() throws Exception
    {
        mockMvc.perform(put("/employee_kpi").contentType("application/json").content(VALID_KPI_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R2: PUT /employee_kpi ADMIN 应 200（写操作放行）")
    @WithMockUser(roles = "ADMIN")
    void r2_admin() throws Exception
    {
        when(employeeKpiService.updateEmployeeKpi(any())).thenReturn(1);
        mockMvc.perform(put("/employee_kpi").contentType("application/json").content(VALID_KPI_BODY))
                .andExpect(status().isOk());
    }

    // ---------------- R3: DELETE /employee_kpi/{ids} ----------------
    @Test
    @DisplayName("R3: DELETE /employee_kpi/1 匿名应 401/403")
    void r3_anonymous() throws Exception
    {
        mockMvc.perform(delete("/employee_kpi/1"))
                .andExpect(r -> {
                    int s = r.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403,
                            "匿名访问应被拒绝（401/403），实际 " + s);
                });
    }

    @Test
    @DisplayName("R3: DELETE /employee_kpi/1 非 ADMIN(USER) 应 403")
    @WithMockUser(roles = "USER")
    void r3_nonAdmin() throws Exception
    {
        mockMvc.perform(delete("/employee_kpi/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R3: DELETE /employee_kpi/1 ADMIN 应 200（删除放行）")
    @WithMockUser(roles = "ADMIN")
    void r3_admin() throws Exception
    {
        when(employeeKpiService.deleteEmployeeKpiByIds(any())).thenReturn(1);
        mockMvc.perform(delete("/employee_kpi/1"))
                .andExpect(status().isOk());
    }

    // ---------------- R4: GET /druid/ ----------------
    @Test
    @DisplayName("R4 (P0-4): GET /druid/ 匿名应 401/403（控制台不裸奔）")
    void r4_anonymous() throws Exception
    {
        mockMvc.perform(get("/druid/"))
                .andExpect(r -> {
                    int s = r.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403,
                            "匿名访问应被拒绝（401/403），实际 " + s);
                });
    }

    @Test
    @DisplayName("R4 (P0-8): GET /druid/ 非 ADMIN(USER) 应 403（S5 角色门槛）")
    @WithMockUser(roles = "USER")
    void r4_nonAdmin() throws Exception
    {
        mockMvc.perform(get("/druid/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R4: GET /druid/ ADMIN 应通过安全门槛（非 401/403/5xx；302 重定向由运行环境复核）")
    @WithMockUser(roles = "ADMIN")
    void r4_admin() throws Exception
    {
        // 测试切片内无 Druid stat servlet，ADMIN 通过后返回 404/302 取决于运行环境；
        // 此处核心验证 ADMIN 角色能越过安全门槛（即不是 401/403，也不应 500）。
        mockMvc.perform(get("/druid/"))
                .andExpect(r -> {
                    int s = r.getResponse().getStatus();
                    boolean passedSecurity = (s != 401 && s != 403 && s < 500);
                    org.junit.jupiter.api.Assertions.assertTrue(passedSecurity,
                            "R4 ADMIN 应越过 ADMIN 门槛（非401/403/5xx），实际 " + s);
                });
    }
}
