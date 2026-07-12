// 实现分页功能
package com.prj.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.prj.common.core.domain.AjaxResult;
import com.prj.common.core.page.PageDomain;
import com.prj.common.core.page.TableDataInfo;
import com.prj.common.core.page.PageParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * 控制器（Web 层）通用基类。
 *
 * <p>职责：
 * 抽取所有 {@code @RestController} 共用的基础能力——基于 PageHelper 的分页处理、
 * 统一分页结果包装（{@link TableDataInfo}）以及增删改操作的统一返回封装（{@link AjaxResult}）。
 *
 * <p>与其他模块的关联（依赖/被依赖）：
 * - 依赖：{@code com.github.pagehelper}（分页插件）、
 *         {@code com.prj.common.core.page.*}（分页参数与结果模型）、
 *         {@code com.prj.common.core.domain.AjaxResult}（统一响应）。
 * - 被依赖：所有业务 Controller（如 EmployeeKpiController、CompareController 等）通过 {@code extends BaseController} 继承。
 *
 * <p>设计意图：避免在多个 Controller 中重复编写分页与返回结果转换样板代码，保证响应结构一致。
 */
public class BaseController
{
    /** 子类复用的日志对象，按实际子类类型生成 logger 实例。 */
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    // 初始化分页对象，设置分页参数
    /**
     * 开启 PageHelper 分页。
     *
     * <p>从请求上下文（前端分页参数）中解析出页码、每页大小与排序字段，
     * 调用 {@code PageHelper.startPage} 在紧随其后的第一条查询上自动附加分页 SQL。
     *
     * <p>依赖：{@link PageParamUtil#createPageRequest()} 负责从请求中提取分页参数。
     * 该方法必须在执行数据库查询（如 service 的 list 方法）之前调用才生效。
     */
    protected void startPage()
    {
        //从前端获取分页参数
        PageDomain pageDomain = PageParamUtil.createPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        String orderBy = pageDomain.getOrderBy();
        //初始化实现分页的对象
        PageHelper.startPage(pageNum, pageSize, orderBy);
    }

    //返回分页数据
    /**
     * 将查询结果列表包装为统一的分页响应对象 {@link TableDataInfo}。
     *
     * @param list 已执行分页查询后返回的结果列表（受 PageHelper 拦截，仅含当前页数据）
     * @return 包含 code、msg、rows（当前页数据）、total（总记录数）的分页结果对象
     */
    protected TableDataInfo getDataByPage(List<?> list)
    {
        TableDataInfo tableData = new TableDataInfo();
        tableData.setCode(200);
        tableData.setMsg("查询成功");
        tableData.setRows(list);
        // 借助 PageInfo 从被 PageHelper 拦截的 list 中解析出 total 总条数
        tableData.setTotal(new PageInfo<>(list).getTotal());
        return tableData;
    }

    /**
     * 响应返回结果
     *
     * @param rows 数据库操作影响行数（新增/修改/删除的条数）
     * @return 操作结果：影响行数 > 0 返回成功，否则返回失败
     */
    protected AjaxResult toAjax(int rows)
    {
        return rows > 0 ? AjaxResult.success() : AjaxResult.error();
    }
}
