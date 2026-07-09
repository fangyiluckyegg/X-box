package com.prj.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public class EmployeeKpi
{
    //private static final long serialVersionUID = 1L;

    /** 员工编号 */
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

    public void setId(Long id) 
    {
        this.id = id;
    }
    public Long getId() 
    {
        return id;
    }

    public void setKpi(String kpi) 
    {
        this.kpi = kpi;
    }
    public String getKpi() 
    {
        return kpi;
    }

    public void setBonus(String bonus) 
    {
        this.bonus = bonus;
    }
    public String getBonus() 
    {
        return bonus;
    }

    public void setManager(String manager) 
    {
        this.manager = manager;
    }
    public String getManager() 
    {
        return manager;
    }

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
