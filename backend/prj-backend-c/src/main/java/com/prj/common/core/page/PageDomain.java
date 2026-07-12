package com.prj.common.core.page;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * 分页领域对象（封装前端分页请求参数）。
 *
 * <p>职责：
 * 承载从请求中提取的分页参数：页码（pageNum）、每页大小（pageSize）、排序列（orderByColumn）与排序方向（isAsc），
 * 并提供 {@link #getOrderBy()} 生成经过白名单校验的排序 SQL 片段，防止 SQL 注入。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code PageParamUtil}（构建本对象）、{@code BaseController}（读取参数并交给 PageHelper）。
 */
public class PageDomain
{
    /** 允许的排序列名格式：仅字母、数字、下划线 */
    private static final Pattern COLUMN_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // 起始索引
    /** 页码。 */
    private Integer pageNum;
    //每页显示数量
    /** 每页记录数。 */
    private Integer pageSize;
    // 排序列
    /** 排序列名。 */
    private String orderByColumn;
    //排序方式 "desc" 或者 "asc"
    /** 排序方向，仅允许 asc/desc，默认 asc。 */
    private String isAsc = "asc";

    /**
     * [P0-FIX] 获取排序字符串，对列名和排序方向进行白名单校验，防止SQL注入。
     * 仅允许字母、数字、下划线组成的列名，排序方向仅允许 asc/desc。
     * 校验不通过时返回空字符串（不排序）。
     */
    public String getOrderBy()
    {
        if (StringUtils.isEmpty(orderByColumn))
        {
            return "";
        }
        // 列名白名单校验：仅允许字母、数字、下划线
        if (!COLUMN_PATTERN.matcher(orderByColumn).matches())
        {
            return "";
        }
        // 排序方向校验：仅允许 asc 或 desc（不区分大小写）
        String direction = isAsc;
        if (StringUtils.isEmpty(direction))
        {
            direction = "asc";
        }
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction))
        {
            return "";
        }
        return orderByColumn + " " + direction.toLowerCase();
    }

    /** 获取页码。 */
    public Integer getPageNum()
    {
        return pageNum;
    }

    /** 设置页码。 */
    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum;
    }

    /** 获取每页大小。 */
    public Integer getPageSize()
    {
        return pageSize;
    }

    /** 设置每页大小。 */
    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }

    /** 获取排序列名。 */
    public String getOrderByColumn()
    {
        return orderByColumn;
    }

    /** 设置排序列名。 */
    public void setOrderByColumn(String orderByColumn)
    {
        this.orderByColumn = orderByColumn;
    }

    /** 获取排序方向。 */
    public String getIsAsc()
    {
        return isAsc;
    }

    /** 设置排序方向（"ascending" 归一为 asc，否则归为 desc）。 */
    public void setIsAsc(String isAsc)
    {
        if ("ascending".equals(isAsc))
        {
            isAsc = "asc";
        }
        else
        {
            isAsc = "desc";
        }
        this.isAsc = isAsc;
    }
}
