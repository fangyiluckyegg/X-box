package com.prj.common.core.page;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public class PageDomain
{
    /** 允许的排序列名格式：仅字母、数字、下划线 */
    private static final Pattern COLUMN_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // 起始索引
    private Integer pageNum;
    //每页显示数量
    private Integer pageSize;
    // 排序列
    private String orderByColumn;
    //排序方式 "desc" 或者 "asc"
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

    public Integer getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum;
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }

    public String getOrderByColumn()
    {
        return orderByColumn;
    }

    public void setOrderByColumn(String orderByColumn)
    {
        this.orderByColumn = orderByColumn;
    }

    public String getIsAsc()
    {
        return isAsc;
    }

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
