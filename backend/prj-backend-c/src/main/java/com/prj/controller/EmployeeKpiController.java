package com.prj.controller;

import java.util.List;

import com.prj.common.core.domain.AjaxResult;
import com.prj.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.prj.domain.EmployeeKpi;
import com.prj.service.IEmployeeKpiService;

import jakarta.validation.Valid;


/**
 * 员工评价管理（EmployeeKpi）REST 控制器。
 *
 * <p>职责：
 * 暴露员工 KPI / 评价数据的增删改查 HTTP 接口，作为 Web 层入口，
 * 将请求转发给 {@link IEmployeeKpiService} 业务层处理，并把结果统一包装为 {@link AjaxResult}/{@link TableDataInfo}。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code IEmployeeKpiService}（业务服务）、{@code EmployeeKpi}（领域实体）、
 *         {@code BaseController}（继承获得分页与结果包装能力）、
 *         Spring Security 的 {@code @PreAuthorize}（接口鉴权）。
 * - 被依赖：前端员工评价管理页面（web/prj-frontend/src/views/employee_kpi）通过对应 api 调用本控制器。
 *
 * <p>鉴权策略：列表/详情查询需登录认证（isAuthenticated）；新增/修改/删除写操作需 ADMIN 角色。
 */
// [P0-FIX] 添加 @Validated 开启控制器级别输入校验
@Validated
@RestController
@RequestMapping("/employee_kpi")
public class EmployeeKpiController extends BaseController
{
    /** 员工评价业务服务，由 Spring 自动注入。 */
    @Autowired
    private IEmployeeKpiService employeeKpiService;

    // [P1-FIX] 添加权限注解，写操作需要 ADMIN 角色

    /** 查询员工评价管理列表     */
    /**
     * 分页查询员工评价列表。
     *
     * @param employeeKpi 查询条件封装（可含字段过滤条件，由框架自动绑定请求参数）
     * @return 统一分页结果 {@link TableDataInfo}（含当前页数据与总条数）
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/list")
    public TableDataInfo list(EmployeeKpi employeeKpi)
    {
        startPage();
        List<EmployeeKpi> list = employeeKpiService.selectEmployeeKpiList(employeeKpi);
        return getDataByPage(list);
    }

    /** 获取员工评价管理详细信息     */
    /**
     * 根据主键 id 查询单条员工评价详情。
     *
     * @param id 员工评价记录主键（路径变量）
     * @return 成功响应，data 为对应 {@link EmployeeKpi} 实体
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return AjaxResult.success(employeeKpiService.selectEmployeeKpiById(id));
    }

    /** 新增员工评价管理     */
    /**
     * 新增一条员工评价记录。
     *
     * @param employeeKpi 待新增的评价实体（@Valid 触发 JSR-303 字段校验）
     * @return 成功/失败响应（依据数据库影响行数）
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    // [P0-FIX] @Valid 触发 EmployeeKpi 上的 JSR-303 约束校验
    public AjaxResult add(@Valid @RequestBody EmployeeKpi employeeKpi)
    {
        return toAjax(employeeKpiService.insertEmployeeKpi(employeeKpi));
    }

    /** 修改员工评价管理     */
    /**
     * 修改一条员工评价记录。
     *
     * @param employeeKpi 待更新的评价实体（@Valid 触发 JSR-303 字段校验）
     * @return 成功/失败响应（依据数据库影响行数）
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    // [P0-FIX] @Valid 触发 EmployeeKpi 上的 JSR-303 约束校验
    public AjaxResult edit(@Valid @RequestBody EmployeeKpi employeeKpi)
    {
        return toAjax(employeeKpiService.updateEmployeeKpi(employeeKpi));
    }

    /** 删除员工评价管理     */
    /**
     * 批量删除员工评价记录。
     *
     * @param ids 待删除记录的主键数组（路径变量，支持一次删除多条）
     * @return 成功/失败响应（依据数据库影响行数）
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(employeeKpiService.deleteEmployeeKpiByIds(ids));
    }
}
