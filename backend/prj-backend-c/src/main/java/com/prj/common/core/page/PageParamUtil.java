package com.prj.common.core.page;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class PageParamUtil
{
    // 起始索引
    public static final String PAGENUM = "pageNum";
    //每页显示数量
    public static final String PAGESIZE = "pageSize";
    //排序列
    public static final String ORDERCOLUMN = "orderByColumn";
    //排序方式 "desc" 或者 "asc"
    public static final String ISASC = "isAsc";

    //获取ServletRequestAttributes对象，ServletRequestAttributes是Spring提供的一个类，用于获取当前请求的相关信息
    //RequestContextHolder是Spring提供的一个工具类，用于获取当前线程绑定的请求上下文信息
    private static String getParameter(String name) 
    {
        ServletRequestAttributes attributes =(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes.getRequest().getParameter(name);
    }

    //构建分页对象
    public static PageDomain createPageRequest() //通过getParameter方法获取从前端传递过来的pageNum、pageSize、orderByColumn、isAsc等参数
    {
        PageDomain pageDomain = new PageDomain();
        pageDomain.setPageNum(Integer.valueOf(getParameter(PAGENUM)));
        pageDomain.setPageSize(Integer.valueOf(getParameter(PAGESIZE)));
        pageDomain.setOrderByColumn(getParameter(ORDERCOLUMN));
        pageDomain.setIsAsc(getParameter(ISASC));
        return pageDomain;
    }
}
