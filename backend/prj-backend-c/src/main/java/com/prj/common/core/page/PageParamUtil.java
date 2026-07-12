package com.prj.common.core.page;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 分页参数工具类。
 *
 * <p>职责：
 * 从当前 HTTP 请求（Spring 请求上下文）中提取分页相关参数，并构建 {@link PageDomain} 对象，
 * 供 {@code BaseController.startPage()} 使用以驱动 PageHelper 分页。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code BaseController}（调用 {@link #createPageRequest()}）。
 * - 依赖：Spring {@code RequestContextHolder}（获取当前请求）。
 */
public class PageParamUtil
{
    // 起始索引
    /** 请求参数名：页码。 */
    public static final String PAGENUM = "pageNum";
    //每页显示数量
    /** 请求参数名：每页大小。 */
    public static final String PAGESIZE = "pageSize";
    //排序列
    /** 请求参数名：排序列。 */
    public static final String ORDERCOLUMN = "orderByColumn";
    //排序方式 "desc" 或者 "asc"
    /** 请求参数名：排序方向。 */
    public static final String ISASC = "isAsc";

    //获取ServletRequestAttributes对象，ServletRequestAttributes是Spring提供的一个类，用于获取当前请求的相关信息
    //RequestContextHolder是Spring提供的一个工具类，用于获取当前线程绑定的请求上下文信息
    /** 从当前请求中按参数名取值（无请求上下文时返回 null）。 */
    private static String getParameter(String name) 
    {
        ServletRequestAttributes attributes =(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes.getRequest().getParameter(name);
    }

    //构建分页对象
    /** 构建分页对象
     * 通过getParameter方法获取从前端传递过来的pageNum、pageSize、orderByColumn、isAsc等参数
     */
    public static PageDomain createPageRequest() //通过getParameter方法获取从前端传递过来的pageNum、pageSize、orderByColumn、isAsc等参数
    {
        PageDomain pageDomain = new PageDomain();
        // [P1-11-FIX] 参数缺失时 NPE 防护，提供默认值 pageNum=1, pageSize=10
        String pageNumStr = getParameter(PAGENUM);
        String pageSizeStr = getParameter(PAGESIZE);
        pageDomain.setPageNum(pageNumStr != null ? Integer.valueOf(pageNumStr) : 1);
        pageDomain.setPageSize(pageSizeStr != null ? Integer.valueOf(pageSizeStr) : 10);
        pageDomain.setOrderByColumn(getParameter(ORDERCOLUMN));
        pageDomain.setIsAsc(getParameter(ISASC));
        return pageDomain;
    }
}
