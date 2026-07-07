package com.prj.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.prj.common.core.domain.AjaxResult;
import com.prj.common.utils.SecurityUtils;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Excel双文件比对控制器
 * [P0-FIX] 使用Ollama bge-m3向量余弦相似度做语义比对，替代简单文本编辑距离
 * 优化：预缓存全部比对文本向量、移除阻塞sleep、OkHttp连接池优化、完整异常日志打印
 */
@Validated
@RestController
@RequestMapping("/api/excel")
public class CompareController {

    private static final Logger logger = LoggerFactory.getLogger(CompareController.class);

    /** 比对结果内存缓存，按用户名键，compare 后供 downloadResult 取用 */
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> RESULT_CACHE = new ConcurrentHashMap<>();

    // ===================== Ollama 向量配置（Docker容器网络）=====================
    private static final String OLLAMA_EMBED_URL = "http://dev-prj-llama:11434/api/embeddings";
    private static final String EMBED_MODEL = "bge-m3:latest";
    /** 语义相似度阈值，和Python代码保持一致0.85，低于该值视为无匹配 */
    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    /** OkHttp增加连接池自动回收，避免长期运行连接泄漏耗尽 */
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(20, 30, TimeUnit.SECONDS))
            .build();

    /**
     * Excel双文件比对接口
     * 读取两个Excel文件第一列单位名称，基于bge-m3向量余弦相似度做语义比对
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

            // 批量预生成原始数据向量，避免重复调用ollama
            Map<String, double[]> originTextEmbeddingMap = batchGetEmbedding(originNames);
            // 过滤向量生成失败的文本
            List<String> validOriginTexts = new ArrayList<>(originTextEmbeddingMap.keySet());
            if (validOriginTexts.isEmpty()) {
                return AjaxResult.error("原始数据全部向量生成失败，请检查Ollama服务是否正常运行");
            }

            // 执行向量语义比对
            List<Map<String, Object>> resultList = performVectorCompare(validOriginTexts, newNames, originTextEmbeddingMap);

            // 缓存比对结果供下载接口使用
            String username = SecurityUtils.getUsername();
            RESULT_CACHE.put(username, resultList);

            logger.info("向量语义比对完成：共 {} 条差异记录", resultList.size());

            // 前端通过 res.data.list 访问结果
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", resultList);
            return AjaxResult.success("比对完成", data);

        } catch (IOException e) {
            logger.error("Excel文件读取失败", e);
            return AjaxResult.error("Excel文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            logger.error("向量语义比对过程异常", e);
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
                logger.error("写入错误响应失败", e);
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
            logger.error("导出Excel失败", e);
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

    // ==================== Excel读取工具方法 ====================
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
                if (value.isEmpty()) {
                    continue;
                }

                // 跳过表头行
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

    // ==================== Ollama 向量核心方法 ====================
    /**
     * 批量获取文本向量，缓存文本-向量映射，移除阻塞sleep避免前端超时
     */
    private Map<String, double[]> batchGetEmbedding(List<String> textList) {
        Map<String, double[]> textVecMap = new HashMap<>();
        for (String text : textList) {
            try {
                double[] vec = getSingleEmbedding(text);
                textVecMap.put(text, vec);
            } catch (Exception e) {
                logger.error("文本[{}]生成向量失败", text, e);
            }
        }
        return textVecMap;
    }

    /**
     * 调用Ollama /api/embeddings 获取单条文本向量
     */
    private double[] getSingleEmbedding(String prompt) throws IOException {
        JSONObject reqJson = new JSONObject();
        reqJson.put("model", EMBED_MODEL);
        reqJson.put("prompt", prompt);
        String reqBody = reqJson.toString();

        RequestBody body = RequestBody.create(reqBody, JSON_MEDIA);
        Request request = new Request.Builder()
                .url(OLLAMA_EMBED_URL)
                .post(body)
                .build();

        try (okhttp3.Response resp = HTTP_CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Ollama接口响应异常，状态码：" + resp.code());
            }
            String respStr = resp.body().string();
            JSONObject respJson = JSON.parseObject(respStr);
            List<Double> embedList = respJson.getList("embedding", Double.class);
            double[] vecArr = new double[embedList.size()];
            for (int i = 0; i < vecArr.length; i++) {
                vecArr[i] = embedList.get(i);
            }
            return vecArr;
        }
    }

    /**
     * 手写余弦相似度计算，对标sklearn cosine_similarity
     */
    private double cosineSimilarity(double[] vecA, double[] vecB) {
        double dotSum = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        int len = vecA.length;
        for (int i = 0; i < len; i++) {
            dotSum += vecA[i] * vecB[i];
            normA += Math.pow(vecA[i], 2);
            normB += Math.pow(vecB[i], 2);
        }
        double normTotal = Math.sqrt(normA) * Math.sqrt(normB);
        if (normTotal == 0) return 0.0;
        return dotSum / normTotal;
    }

    // ==================== 优化后向量比对逻辑（预缓存全部比对文本向量，无重复请求） ====================
    private List<Map<String, Object>> performVectorCompare(
            List<String> originTexts,
            List<String> newTexts,
            Map<String, double[]> originVecMap
    ) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        boolean[] newTextMatchedFlag = new boolean[newTexts.size()];
        // 一次性缓存所有比对文本向量，只请求一次ollama，大幅减少接口调用次数
        Map<String, double[]> newTextVecCache = batchGetEmbedding(newTexts);

        // 1. 遍历原始每条单位，在比对列表中找最佳语义匹配
        for (String originText : originTexts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", originText);
            item.put("key", originText);
            item.put("originVal", originText);

            double[] originVec = originVecMap.get(originText);
            int bestMatchIdx = -1;
            double maxSim = 0.0;

            // 遍历所有比对文本，直接从缓存读取向量，不再重复调用ollama
            for (int i = 0; i < newTexts.size(); i++) {
                String newText = newTexts.get(i);
                double[] newVec = newTextVecCache.get(newText);
                if (newVec == null) {
                    logger.warn("比对文本[{}]向量生成失败，跳过本条匹配计算", newText);
                    continue;
                }
                double sim = cosineSimilarity(originVec, newVec);
                if (sim > maxSim) {
                    maxSim = sim;
                    bestMatchIdx = i;
                }
            }

            // 分支判断匹配类型
            if (bestMatchIdx == -1) {
                // 所有比对文本向量生成全部失败，无匹配
                item.put("matchedName", "");
                item.put("newVal", "");
                item.put("similarity", 0.0);
                item.put("diffType", "未匹配");
            } else {
                String bestNewText = newTexts.get(bestMatchIdx);
                // 精确完全相等
                if (originText.equalsIgnoreCase(bestNewText.trim())) {
                    newTextMatchedFlag[bestMatchIdx] = true;
                    item.put("matchedName", bestNewText);
                    item.put("newVal", bestNewText);
                    item.put("similarity", 1.0);
                    item.put("diffType", "完全匹配");
                } else if (maxSim >= SIMILARITY_THRESHOLD) {
                    // 语义模糊匹配（向量相似度≥0.85）
                    newTextMatchedFlag[bestMatchIdx] = true;
                    item.put("matchedName", bestNewText);
                    item.put("newVal", bestNewText);
                    item.put("similarity", Math.round(maxSim * 100) / 100.0);
                    item.put("diffType", "语义模糊匹配");
                } else {
                    // 语义差距大，无匹配
                    item.put("matchedName", "");
                    item.put("newVal", "");
                    item.put("similarity", Math.round(maxSim * 100) / 100.0);
                    item.put("diffType", "未匹配");
                }
            }
            resultList.add(item);
        }

        // 2. 找出比对文件中未被匹配的新增单位
        for (int i = 0; i < newTexts.size(); i++) {
            if (!newTextMatchedFlag[i]) {
                String newText = newTexts.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", "");
                item.put("key", newText);
                item.put("originVal", "");
                item.put("matchedName", newText);
                item.put("newVal", newText);
                item.put("similarity", 0.0);
                item.put("diffType", "新增项");
                resultList.add(item);
            }
        }
        return resultList;
    }

    // ==================== Map取值工具方法 ====================
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