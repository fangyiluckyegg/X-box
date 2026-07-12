package com.prj.mapper;

import java.util.List;
import com.prj.domain.EmployeeKpi;

/**
 * 员工评价管理 Mapper 接口（数据访问层）。
 *
 * <p>职责：
 * 定义员工评价表（employee_kpi）的 CRUD 数据库操作契约，由 MyBatis 通过 XML/注解方式实现。
 * 配合 PageHelper 使用时，list 查询会被自动分页。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code EmployeeKpi} 领域实体。
 * - 被依赖：{@code EmployeeKpiServiceImpl} 调用本接口完成持久化。
 * - 对应 SQL 通常位于 resources 下的 Mapper XML（如 EmployeeKpiMapper.xml）。
 */
/** 员工评价管理Mapper接口 */
public interface EmployeeKpiMapper 
{
    /** 查询员工评价管理
     * @param id 员工评价管理主键
     * @return 员工评价管理
     */
    public EmployeeKpi selectEmployeeKpiById(Long id);

    /** 查询员工评价管理列表
     * @param employeeKpi 员工评价管理（作为动态查询条件）
     * @return 员工评价管理集合
     */
    public List<EmployeeKpi> selectEmployeeKpiList(EmployeeKpi employeeKpi);

    /** 新增员工评价管理
     * @param employeeKpi 员工评价管理
     * @return 结果（影响行数）
     */
    public int insertEmployeeKpi(EmployeeKpi employeeKpi);

    /** 修改员工评价管理
     * @param employeeKpi 员工评价管理
     * @return 结果（影响行数）
     */
    public int updateEmployeeKpi(EmployeeKpi employeeKpi);

    /** 删除员工评价管理
     * @param id 员工评价管理主键
     * @return 结果（影响行数）
     */
    public int deleteEmployeeKpiById(Long id);

    /** 批量删除员工评价管理
     * @param ids 需要删除的数据主键集合
     * @return 结果（影响行数）
     */
    public int deleteEmployeeKpiByIds(Long[] ids);
}
