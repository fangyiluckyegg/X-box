package com.prj.common.core.page;

import java.io.Serializable;
import java.util.List;

/**
 * 分页表格数据响应对象。
 *
 * <p>职责：
 * 统一封装带分页的列表查询响应：total（总记录数）、rows（当前页数据列表）、code（状态码）、msg（消息）。
 * 与 {@code AjaxResult} 并列，专门用于分页场景（由 {@code BaseController.getDataByPage} 填充）。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code BaseController}（构造并返回）、各 Controller 的 list 接口（如 EmployeeKpiController）。
 */
public class TableDataInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;
    /** 列表数据 */
    private List<?> rows;
    /** 消息状态码 */
    private int code;
    /** 消息内容 */
    private String msg;
    /**     * 表格数据对象     */
    public TableDataInfo()
    {
    }

    /**分页 
     * @param list 列表数据
     * @param total 总记录数
     */
    public TableDataInfo(List<?> list, int total)
    {
        this.rows = list;
        this.total = total;
    }

    /** 获取总记录数。 */
    public long getTotal()
    {
        return total;
    }
    /** 设置总记录数。 */
    public void setTotal(long total)
    {
        this.total = total;
    }

    /** 获取当前页数据列表。 */
    public List<?> getRows()
    {
        return rows;
    }
    /** 设置当前页数据列表。 */
    public void setRows(List<?> rows)
    {
        this.rows = rows;
    }

    /** 获取状态码。 */
    public int getCode()
    {
        return code;
    }
    /** 设置状态码。 */
    public void setCode(int code)
    {
        this.code = code;
    }

    /** 获取消息内容。 */
    public String getMsg()
    {
        return msg;
    }
    /** 设置消息内容。 */
    public void setMsg(String msg)
    {
        this.msg = msg;
    }
}
