package com.prj.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * 员工评价管理领域实体（EmployeeKpi）。
 *
 * <p>职责：
 * 映射数据库员工评价表的核心业务对象，承载"员工编号 / 考评结果 / 奖金 / 考评人"等字段，
 * 并借助 JSR-303 注解（@NotBlank/@Size）声明字段校验规则，供 Controller 层 @Valid 触发。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code EmployeeKpiMapper}（持久化）、{@code IEmployeeKpiService}（业务处理）、
 *           {@code EmployeeKpiController}（请求/响应载体）。
 * - 校验约束与上方 [P0-FIX] 备注一致：kpi（考评结果）与 manager（考评人）非空，各字段长度受限。
 */
public class EmployeeKpi
{
    //private static final long serialVersionUID = 1L;

    /** 员工编号（主键） */
    private Long id;
    /** 考评结果 */
    // [P0-FIX] 输入校验：考评结果不能为空
    @NotBlank(message = "考评结果不能为空")
    @Size(max = 100, message = "考评结果长度不能超过100个字符")
    private String kpi;
    /** 奖金 */
    @Size(max = 50, message = "奖金长度不能超过50个字符")
    private String bonus;
    /** 考评人 */
    // [P0-FIX] 输入校验：考评人不能为空
    @NotBlank(message = "考评人不能为空")
    @Size(max = 50, message = "考评人长度不能超过50个字符")
    private String manager;

    /** 设置员工编号。 */
    public void setId(Long id) 
    {
        this.id = id;
    }
    /** 获取员工编号。 */
    public Long getId() 
    {
        return id;
    }

    /** 设置考评结果。 */
    public void setKpi(String kpi) 
    {
        this.kpi = kpi;
    }
    /** 获取考评结果。 */
    public String getKpi() 
    {
        return kpi;
    }

    /** 设置奖金。 */
    public void setBonus(String bonus) 
    {
        this.bonus = bonus;
    }
    /** 获取奖金。 */
    public String getBonus() 
    {
        return bonus;
    }

    /** 设置考评人。 */
    public void setManager(String manager) 
    {
        this.manager = manager;
    }
    /** 获取考评人。 */
    public String getManager() 
    {
        return manager;
    }

    /** 以多行格式输出对象字段，便于日志打印与调试。 */
    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("kpi", getKpi())
            .append("bonus", getBonus())
            .append("manager", getManager())
            .toString();
    }
}
