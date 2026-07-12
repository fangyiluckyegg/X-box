package com.prj.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.prj.mapper.EmployeeKpiMapper;
import com.prj.domain.EmployeeKpi;
import com.prj.service.IEmployeeKpiService;

/**
 * 员工评价管理业务服务实现类。
 *
 * <p>职责：
 * 实现 {@link IEmployeeKpiService} 定义的增删改查业务逻辑，将请求委派给
 * {@code EmployeeKpiMapper} 完成数据库操作（典型的 Service 层薄封装）。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code EmployeeKpiMapper}（数据访问）、{@code EmployeeKpi}（实体）。
 * - 被依赖：{@code EmployeeKpiController}。
 */
@Service
public class EmployeeKpiServiceImpl implements IEmployeeKpiService 
{
    /** 员工评价Mapper，由 Spring 自动注入。 */
    @Autowired
    private EmployeeKpiMapper employeeKpiMapper;

    /** 根据主键查询单条员工评价（委托 Mapper 执行）。 */
    @Override
    public EmployeeKpi selectEmployeeKpiById(Long id)
    {
        return employeeKpiMapper.selectEmployeeKpiById(id);
    }

    /** 条件查询员工评价列表（配合 PageHelper 时可被分页）。 */
    @Override
    public List<EmployeeKpi> selectEmployeeKpiList(EmployeeKpi employeeKpi)
    {
        return employeeKpiMapper.selectEmployeeKpiList(employeeKpi);
    }

    /** 新增员工评价（委托 Mapper 执行插入）。 */
    @Override
    public int insertEmployeeKpi(EmployeeKpi employeeKpi)
    {
        return employeeKpiMapper.insertEmployeeKpi(employeeKpi);
    }

    /** 更新员工评价（委托 Mapper 执行更新）。 */
    @Override
    public int updateEmployeeKpi(EmployeeKpi employeeKpi)
    {
        return employeeKpiMapper.updateEmployeeKpi(employeeKpi);
    }

    /** 批量删除员工评价（委托 Mapper 执行）。 */
    @Override
    public int deleteEmployeeKpiByIds(Long[] ids)
    {
        return employeeKpiMapper.deleteEmployeeKpiByIds(ids);
    }

    /** 根据主键删除单条员工评价（委托 Mapper 执行）。 */
    @Override
    public int deleteEmployeeKpiById(Long id)
    {
        return employeeKpiMapper.deleteEmployeeKpiById(id);
    }
}
