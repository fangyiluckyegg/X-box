package com.prj.controller;

import com.prj.common.core.domain.AjaxResult;
import com.prj.common.utils.SecurityUtils;
import com.prj.service.compare.ICompareService;
import com.prj.store.IProgressStore;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 双文件比对控制器（Compare）。
 *
 * <p>重构后仅保留 HTTP 边界职责：
 * 1. 接收"原始 Excel"与"比对 Excel"，在请求线程内做同步校验（空/类型/大小）；
 * 2. 校验失败立即返回 {@link AjaxResult#error}（body code=500，由前端拦截器提示）；
 * 3. 校验通过：cancel 覆盖旧任务 → 写入 uploaded 占位进度 → 返回 {@code 202 Accepted}，
 *    并调度 {@code ICompareService.performCompare}（@Async 异步编排）；
 * 4. 轮询 {@code /progress}、{@code /fetchResult}、{@code /downloadResult} 均改读 {@code IProgressStore}（Spring 单例），
 *    不再依赖任何 static Map。
 *
 * <p>进度/结果存储、向量化、余弦比对、Excel 读取均已下沉为 Service / Store 层（见 com.prj.service.*、com.prj.store.*）。
 */
@Validated
@RestController
@RequestMapping("/api/excel")
public class CompareController
{
    /** 类级日志对象。 */
    private static final Logger logger = LoggerFactory.getLogger(CompareController.class);

    /** 单文件大小上限（20MB），与前端 checkFile 对齐。 */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final ICompareService compareService;
    private final IProgressStore progressStore;

    @Autowired
    public CompareController(ICompareService compareService, IProgressStore progressStore)
    {
        this.compareService = compareService;
        this.progressStore = progressStore;
    }

    // ===================== 进度查询接口 前端轮询调用 =====================
    /**
     * 查询当前用户的实时比对进度。
     *
     * @return 若无进行中任务返回"无任务"（data=null）；否则返回进度 VO（stage/percent/current/total/currentText/message）
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/progress")
    public AjaxResult getProgress()
    {
        String username = SecurityUtils.getUsername();
        ProgressVo progress = progressStore.getProgress(username);
        if (progress == null)
        {
            return AjaxResult.success("无任务", null);
        }
        return AjaxResult.success(progress);
    }

    // ===================== 获取已完成比对结果 =====================
    /**
     * 获取当前用户最近一次已完成的比对结果（progress stage=done 后调用）。
     * 用于前端纯轮询架构：/compare 触发后不等待，轮询到 done 时调此接口取结果。
     *
     * @return 比对结果列表（data={list: [...]}，无结果时 list 为空）
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/fetchResult")
    public AjaxResult fetchResult()
    {
        String username = SecurityUtils.getUsername();
        List<CompareResultRow> result = progressStore.getResult(username);
        if (result == null || result.isEmpty())
        {
            return AjaxResult.success("无比对结果", Collections.singletonMap("list", Collections.emptyList()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", result);
        return AjaxResult.success("获取成功", data);
    }

    // ===================== 开始比对主接口 =====================
    /**
     * 提交比对（fire-and-forget）：请求线程内同步校验后返回 202，真正比对在 @Async worker 执行。
     *
     * @param originExcel 原始 Excel 文件（待比对基准）
     * @param newExcel    比对 Excel 文件（新版本/待核对版本）
     * @return 202 Accepted（body code=200，兼容前端 request.js 拦截器）；校验失败返回 AjaxResult.error
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/compare")
    public ResponseEntity<AjaxResult> compareExcel(
            @RequestParam("originExcel") MultipartFile originExcel,
            @RequestParam("newExcel") MultipartFile newExcel)
    {
        String username = SecurityUtils.getUsername();

        // 请求线程内同步校验（空/类型/大小），失败立即返回错误（前端拦截器 Message.error）
        AjaxResult validationError = validateFile(originExcel, "原始");
        if (validationError != null)
        {
            return ResponseEntity.ok(validationError);
        }
        validationError = validateFile(newExcel, "比对");
        if (validationError != null)
        {
            return ResponseEntity.ok(validationError);
        }

        // 在请求线程内（202 返回之前）把上传文件读成字节数组，
        // 避免异步 worker 再去读 Tomcat 上传临时文件——请求线程结束、临时文件被清理后会抛 IOException。
        byte[] originBytes;
        byte[] newBytes;
        try
        {
            originBytes = originExcel.getBytes();
            newBytes = newExcel.getBytes();
        }
        catch (IOException e)
        {
            logger.error("读取上传文件字节失败", e);
            return ResponseEntity.ok(AjaxResult.error("读取上传文件失败"));
        }

        // 重提覆盖旧任务（清除旧进度/结果，避免脏进度残留）
        progressStore.cancel(username);
        ProgressVo uploaded = new ProgressVo();
        uploaded.setStage("uploaded");
        uploaded.setPercent(0);
        uploaded.setCurrent(0);
        uploaded.setTotal(0);
        uploaded.setCurrentText("文件已上传，等待比对");
        progressStore.saveProgress(username, uploaded);

        // 异步提交比对任务（@Async worker 执行编排，进度经 progressStore 回写）
        // 传入请求线程内已读出的字节数组，worker 不再依赖 Tomcat 临时文件
        compareService.performCompare(username, originBytes, newBytes);

        // 202 Accepted + body code=200，前端 request.js 拦截器按 code 判定成功，不受影响
        return ResponseEntity.status(202).body(AjaxResult.success("任务已提交"));
    }

    /**
     * 同步校验上传文件（空/类型/大小）。
     *
     * @param file  上传文件
     * @param label 文件标签（用于错误提示）
     * @return 校验失败返回 AjaxResult.error；通过返回 null
     */
    private AjaxResult validateFile(MultipartFile file, String label)
    {
        if (file == null || file.isEmpty())
        {
            return AjaxResult.error(label + "文件不能为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls")))
        {
            return AjaxResult.error(label + "文件类型不支持，仅支持 .xlsx/.xls");
        }
        if (file.getSize() > MAX_FILE_SIZE)
        {
            return AjaxResult.error(label + "文件超过 20MB 上限");
        }
        return null;
    }

    // ===================== 导出比对结果为 Excel =====================
    /**
     * 导出比对结果为 Excel 文件。
     *
     * @param response HTTP 响应，写入 Excel 附件流；无比对结果时返回 JSON 错误提示
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/downloadResult")
    public void downloadResult(HttpServletResponse response)
    {
        String username = SecurityUtils.getUsername();
        List<CompareResultRow> list = progressStore.getResult(username);
        if (list == null || list.isEmpty())
        {
            try
            {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\":500,\"msg\":\"无比对结果，请先执行比对\"}");
            }
            catch (IOException e)
            {
                logger.error("导出错误响应失败", e);
            }
            return;
        }

        Workbook wb = null;
        OutputStream out = null;
        try
        {
            wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("比对结果");
            org.apache.poi.ss.usermodel.CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "原始名称", "匹配名称", "相似度", "差异类型"};
            for (int i = 0; i < headers.length; i++)
            {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < list.size(); i++)
            {
                CompareResultRow item = list.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(item.getName());
                row.createCell(2).setCellValue(item.getMatchedName());
                row.createCell(3).setCellValue(item.getSimilarity());
                row.createCell(4).setCellValue(item.getDiffType());
            }
            for (int i = 0; i < headers.length; i++)
            {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=比对结果.xlsx");
            out = response.getOutputStream();
            wb.write(out);
            out.flush();
        }
        catch (IOException e)
        {
            logger.error("Excel导出IO写入异常", e);
        }
        finally
        {
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (IOException e)
                {
                    logger.error("输出流关闭IO异常", e);
                }
            }
            if (wb != null)
            {
                try
                {
                    wb.close();
                }
                catch (IOException e)
                {
                    logger.error("工作簿关闭IO异常", e);
                }
            }
        }
    }
}
