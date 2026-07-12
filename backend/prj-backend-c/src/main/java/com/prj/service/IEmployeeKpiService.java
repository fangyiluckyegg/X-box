package com.prj.service;

import java.util.List;
import com.prj.domain.EmployeeKpi;

/**
 * 员工评价管理业务服务接口（Service 层契约）。
 *
 * <p>职责：
 * 定义员工评价业务的能力集合（增删改查），供 Controller 调用，
 * 具体实现见 {@code com.prj.service.impl.EmployeeKpiServiceImpl}。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code EmployeeKpi} 领域实体。
 * - 被依赖：{@code EmployeeKpiController}。
 */
public interface IEmployeeKpiService
{
    /**
     * 根据主键查询单条员工评价。
     * @param id 主键
     * @return 员工评价实体
     */
    public EmployeeKpi selectEmployeeKpiById(Long id);
    /**
     * 条件查询员工评价列表。
     * @param employeeKpi 查询条件
     * @return 员工评价列表
     */
    public List<EmployeeKpi> selectEmployeeKpiList(EmployeeKpi employeeKpi);
    /**
     * 新增员工评价。
     * @param employeeKpi 待新增实体
     * @return 影响行数
     */
    public int insertEmployeeKpi(EmployeeKpi employeeKpi);
    /**
     * 更新员工评价。
     * @param employeeKpi 待更新实体
     * @return 影响行数
     */
    public int updateEmployeeKpi(EmployeeKpi employeeKpi);
    /**
     * 批量删除员工评价。
     * @param ids 主键数组
     * @return 影响行数
     */
    public int deleteEmployeeKpiByIds(Long[] ids);
    /**
     * 根据主键删除单条员工评价。
     * @param id 主键
     * @return 影响行数
     */
    public int deleteEmployeeKpiById(Long id);
}
