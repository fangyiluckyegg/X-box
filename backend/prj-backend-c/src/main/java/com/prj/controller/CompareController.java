package com.prj.controller;

import com.prj.common.core.domain.AjaxResult;
import com.prj.common.utils.SecurityUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Excel双文件比对控制器
 * [P0-FIX] 实现完整的单位名称比对逻辑（精确匹配 + 模糊匹配）及结果导出
 */
@Validated
@RestController
@RequestMapping("/api/excel")
public class CompareController {

    private static final Logger logger = LoggerFactory.getLogger(CompareController.class);

    /** 比对结果内存缓存，按用户名键，compare 后供 downloadResult 取用 */
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> RESULT_CACHE = new ConcurrentHashMap<>();

    /** 模糊匹配相似度阈值（低于此值视为未匹配） */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    /**
     * Excel双文件比对接口
     * 读取两个Excel文件的第一列名称，进行精确匹配与模糊匹配，返回比对差异列表。
     *
     * @param originExcel 原始数据Excel文件
     * @param newExcel    比对数据Excel文件
     * @return AjaxResult 包含 list 字段，每项含 name/matchedName/similarity/key/originVal/newVal/diffType
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/compare")
    public AjaxResult compareExcel(
            @RequestParam("originExcel") MultipartFile originExcel,
            @RequestParam("newExcel") MultipartFile newExcel
    ) {
        // 参数校验
        if (originExcel == null || originExcel.isEmpty()) {
            return AjaxResult.error("原始数据文件不能为空");
        }
        if (newExcel == null || newExcel.isEmpty()) {
            return AjaxResult.error("比对数据文件不能为空");
        }

        try {
            // 读取两个 Excel 文件的第一列名称列表
            List<String> originNames = readFirstColumnNames(originExcel);
            List<String> newNames = readFirstColumnNames(newExcel);

            if (originNames.isEmpty()) {
                return AjaxResult.error("原始数据文件未读取到有效名称");
            }
            if (newNames.isEmpty()) {
                return AjaxResult.error("比对数据文件未读取到有效名称");
            }

            logger.info("比对开始：原始数据 {} 条，比对数据 {} 条", originNames.size(), newNames.size());

            // 执行比对
            List<Map<String, Object>> resultList = performComparison(originNames, newNames);

            // 缓存比对结果供下载接口使用
            String username = SecurityUtils.getUsername();
            RESULT_CACHE.put(username, resultList);

            logger.info("比对完成：共 {} 条差异记录", resultList.size());

            // 前端通过 res.data.list 访问结果，需将 list 嵌套在 AjaxResult 的 data 字段内
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", resultList);
            return AjaxResult.success("比对完成", data);

        } catch (IOException e) {
            logger.error("Excel文件读取失败: {}", e.getMessage(), e);
            return AjaxResult.error("Excel文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            logger.error("比对过程异常: {}", e.getMessage(), e);
            return AjaxResult.error("比对过程异常：" + e.getMessage());
        }
    }

    /**
     * 下载比对结果Excel
     * 从内存缓存中取出当前用户的比对结果，导出为 .xlsx 文件。
     *
     * @param response HttpServletResponse 用于写入文件流
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/downloadResult")
    public void downloadResult(HttpServletResponse response) {
        String username = SecurityUtils.getUsername();
        List<Map<String, Object>> resultList = RESULT_CACHE.get(username);

        if (resultList == null || resultList.isEmpty()) {
            try {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"msg\":\"没有可下载的比对结果，请先执行比对\"}");
            } catch (IOException e) {
                logger.error("写入错误响应失败: {}", e.getMessage());
            }
            return;
        }

        Workbook workbook = null;
        OutputStream outputStream = null;
        try {
            workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("比对结果");

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 写表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "原始名称", "匹配名称", "相似度", "差异类型"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 写数据行
            for (int i = 0; i < resultList.size(); i++) {
                Map<String, Object> item = resultList.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(getStringValue(item, "name"));
                row.createCell(2).setCellValue(getStringValue(item, "matchedName"));
                row.createCell(3).setCellValue(getDoubleValue(item, "similarity"));
                row.createCell(4).setCellValue(getStringValue(item, "diffType"));
            }

            // 自动列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=compare_result.xlsx");
            response.setHeader("Access-Control-Allow-Origin", "*");

            outputStream = response.getOutputStream();
            workbook.write(outputStream);
            outputStream.flush();

            logger.info("比对结果Excel导出成功，共 {} 条记录", resultList.size());

        } catch (IOException e) {
            logger.error("导出Excel失败: {}", e.getMessage(), e);
        } finally {
            // 释放资源
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 从 Excel 文件中读取第一列的所有非空文本值
     */
    private List<String> readFirstColumnNames(MultipartFile file) throws IOException {
        List<String> names = new ArrayList<>();
        Workbook workbook = WorkbookFactory.create(file.getInputStream());
        try {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return names;
            }

            // 从第一行开始读取（跳过可能的表头行）
            boolean firstRow = true;
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                Cell cell = row.getCell(0);
                if (cell == null) {
                    continue;
                }

                String value = getCellStringValue(cell).trim();

                // 跳过空值
                if (value.isEmpty()) {
                    continue;
                }

                // 跳过可能的表头（第一行如果包含"名称"/"单位"等关键词则视为表头）
                if (firstRow) {
                    firstRow = false;
                    if (value.contains("名称") || value.contains("单位") || value.contains("序号")
                            || value.equalsIgnoreCase("name")) {
                        continue;
                    }
                }

                names.add(value);
            }
        } finally {
            workbook.close();
        }
        return names;
    }

    /**
     * 获取单元格的字符串值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // 避免数字被转为科学计数法
                double numVal = cell.getNumericCellValue();
                if (numVal == Math.floor(numVal)) {
                    return String.valueOf((long) numVal);
                }
                return String.valueOf(numVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * 执行名称比对逻辑
     * 对原始列表中的每个名称，在比对列表中查找精确匹配或最佳模糊匹配
     * 同时检测比对列表中不存在于原始列表的多余项
     *
     * @param originNames 原始名称列表
     * @param newNames     比对名称列表
     * @return 比对结果列表
     */
    private List<Map<String, Object>> performComparison(List<String> originNames, List<String> newNames) {
        List<Map<String, Object>> resultList = new ArrayList<>();

        // 用于追踪比对列表中已被匹配的项（检测多余项）
        boolean[] newMatched = new boolean[newNames.size()];

        // 1. 遍历原始列表，查找匹配
        for (String originName : originNames) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", originName);
            item.put("key", originName);
            item.put("originVal", originName);

            // 精确匹配（不区分大小写、去除首尾空格）
            int exactMatchIndex = -1;
            for (int i = 0; i < newNames.size(); i++) {
                if (originName.equalsIgnoreCase(newNames.get(i).trim())) {
                    exactMatchIndex = i;
                    break;
                }
            }

            if (exactMatchIndex >= 0) {
                // 精确匹配成功
                newMatched[exactMatchIndex] = true;
                item.put("matchedName", newNames.get(exactMatchIndex));
                item.put("newVal", newNames.get(exactMatchIndex));
                item.put("similarity", 1.0);
                item.put("diffType", "完全匹配");
            } else {
                // 模糊匹配：寻找相似度最高的项
                int bestIndex = -1;
                double bestSimilarity = 0.0;

                for (int i = 0; i < newNames.size(); i++) {
                    double similarity = calculateSimilarity(originName, newNames.get(i));
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity;
                        bestIndex = i;
                    }
                }

                if (bestIndex >= 0 && bestSimilarity >= SIMILARITY_THRESHOLD) {
                    // 模糊匹配成功
                    newMatched[bestIndex] = true;
                    item.put("matchedName", newNames.get(bestIndex));
                    item.put("newVal", newNames.get(bestIndex));
                    item.put("similarity", Math.round(bestSimilarity * 100) / 100.0);
                    item.put("diffType", "模糊匹配");
                } else {
                    // 未找到匹配
                    item.put("matchedName", "");
                    item.put("newVal", "");
                    item.put("similarity", bestIndex >= 0 ? Math.round(bestSimilarity * 100) / 100.0 : 0.0);
                    item.put("diffType", "未匹配");
                }
            }

            resultList.add(item);
        }

        // 2. 检测比对列表中多余项（不存在于原始列表）
        for (int i = 0; i < newNames.size(); i++) {
            if (!newMatched[i]) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", "");
                item.put("key", newNames.get(i));
                item.put("originVal", "");
                item.put("matchedName", newNames.get(i));
                item.put("newVal", newNames.get(i));
                item.put("similarity", 0.0);
                item.put("diffType", "新增项");
                resultList.add(item);
            }
        }

        return resultList;
    }

    /**
     * 计算两个字符串的相似度（基于 Levenshtein 距离）
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 相似度 [0.0, 1.0]
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.isEmpty() && s2.isEmpty()) {
            return 1.0;
        }

        // 检查包含关系（短串被长串包含时给予较高相似度）
        String shorter = s1.length() <= s2.length() ? s1 : s2;
        String longer = s1.length() > s2.length() ? s1 : s2;
        if (longer.contains(shorter) && !shorter.isEmpty()) {
            return (double) shorter.length() / longer.length() * 0.8 + 0.2;
        }

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * 计算两个字符串的 Levenshtein 编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        // 优化：确保 s1 是较短的字符串，减少空间
        if (len1 > len2) {
            String temp = s1;
            s1 = s2;
            s2 = temp;
            len1 = s1.length();
            len2 = s2.length();
        }

        int[] prev = new int[len1 + 1];
        int[] curr = new int[len1 + 1];

        for (int i = 0; i <= len1; i++) {
            prev[i] = i;
        }

        for (int j = 1; j <= len2; j++) {
            curr[0] = j;
            for (int i = 1; i <= len1; i++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[i] = Math.min(Math.min(curr[i - 1] + 1, prev[i] + 1), prev[i - 1] + cost);
            }
            // 交换 prev 和 curr
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[len1];
    }

    /**
     * 安全地从 Map 中获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    /**
     * 安全地从 Map 中获取 double 值
     */
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
