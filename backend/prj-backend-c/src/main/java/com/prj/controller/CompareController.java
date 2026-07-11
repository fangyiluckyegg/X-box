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

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Excel双文件比对控制器
 * 新增实时进度查询接口，前端轮询展示进度条
 * 修复：导出Excel close() IO异常捕获，无编译报错
 */
@Validated
@RestController
@RequestMapping("/api/excel")
public class CompareController {

    private static final Logger logger = LoggerFactory.getLogger(CompareController.class);

    // 比对结果缓存 key=登录用户名
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> RESULT_CACHE = new ConcurrentHashMap<>();
    // 用户比对进度缓存 key=登录用户名
    private static final ConcurrentHashMap<String, CompareProgress> PROGRESS_CACHE = new ConcurrentHashMap<>();

    // ===================== 进度实体（线程安全）=====================
    public static class CompareProgress {
        public final AtomicInteger total = new AtomicInteger(0);
        public final AtomicInteger done = new AtomicInteger(0);
        public volatile String currentText = "";
        // vector_calc / match_compare / done
        public volatile String stage = "";

        public double getPercent() {
            int t = total.get();
            int d = done.get();
            if (t <= 0) return 0.00;
            return Math.round((d * 100.0 / t) * 100) / 100.0;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("total", total.get());
            obj.put("done", done.get());
            obj.put("percent", getPercent());
            obj.put("currentText", currentText);
            obj.put("stage", stage);
            return obj;
        }
    }

    // ===================== 进度查询接口 前端轮询调用 =====================
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/progress")
    public AjaxResult getProgress() {
        String username = SecurityUtils.getUsername();
        CompareProgress progress = PROGRESS_CACHE.get(username);
        if (progress == null) {
            return AjaxResult.success("无任务", null);
        }
        return AjaxResult.success(progress.toJson());
    }

    // ===================== Ollama 常量 =====================
    private static final String OLLAMA_EMBED_URL = "http://dev-prj-llama:11434/api/embeddings";
    private static final String EMBED_MODEL = "bge-m3:latest";
    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(20, 30, TimeUnit.SECONDS))
            .build();

    /**
     * 开始比对主接口
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/compare")
    public AjaxResult compareExcel(
            @RequestParam("originExcel") MultipartFile originExcel,
            @RequestParam("newExcel") MultipartFile newExcel
    ) {
        if (originExcel == null || originExcel.isEmpty()) {
            return AjaxResult.error("原始文件不能为空");
        }
        if (newExcel == null || newExcel.isEmpty()) {
            return AjaxResult.error("比对文件不能为空");
        }

        String username = SecurityUtils.getUsername();
        CompareProgress progress = new CompareProgress();
        PROGRESS_CACHE.put(username, progress);

        try {
            List<String> originList = readFirstColumnNames(originExcel);
            List<String> newList = readFirstColumnNames(newExcel);

            if (originList.isEmpty()) {
                PROGRESS_CACHE.remove(username);
                return AjaxResult.error("原始文件无有效数据");
            }
            progress.total.set(originList.size());
            logger.info("比对启动，原始数据总数：{}", originList.size());

            // 阶段1：向量计算
            progress.stage = "vector_calc";
            Map<String, double[]> originVecMap = batchEmbedding(originList, progress);
            List<String> validOrigin = new ArrayList<>(originVecMap.keySet());
            if (validOrigin.isEmpty()) {
                PROGRESS_CACHE.remove(username);
                return AjaxResult.error("全部向量生成失败，请检查Ollama容器");
            }

            // 阶段2：相似度匹配
            progress.stage = "match_compare";
            List<Map<String, Object>> result = matchProcess(validOrigin, newList, originVecMap, progress);

            // 全部完成
            progress.stage = "done";
            progress.done.set(progress.total.get());
            progress.currentText = "比对完成";
            RESULT_CACHE.put(username, result);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", result);
            return AjaxResult.success("比对完成", data);
        } catch (Exception e) {
            logger.error("比对异常", e);
            return AjaxResult.error("比对失败：" + e.getMessage());
        } finally {
            // 5分钟后自动清理进度与结果缓存。
            // [CODE-REVIEW-FIX] 原实现仅清理 PROGRESS_CACHE，RESULT_CACHE（按用户名缓存的比对结果）
            // 永不回收，导致静态 Map 随比对次数无限增长，形成内存泄漏。此处一并回收，
            // 将其生命周期与既有 5 分钟进度窗口对齐（超时后前端再下载会提示"无比对结果"，属预期行为）。
            new Thread(() -> {
                try {
                    TimeUnit.MINUTES.sleep(5);
                    PROGRESS_CACHE.remove(username);
                    RESULT_CACHE.remove(username);
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }

    /**
     * 导出结果Excel（已修复IO未捕获异常编译报错）
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/downloadResult")
    public void downloadResult(HttpServletResponse response) {
        String username = SecurityUtils.getUsername();
        List<Map<String, Object>> list = RESULT_CACHE.get(username);
        if (list == null || list.isEmpty()) {
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\":500,\"msg\":\"无比对结果，请先执行比对\"}");
            } catch (IOException e) {
                logger.error("导出错误响应失败", e);
            }
            return;
        }

        Workbook wb = null;
        OutputStream out = null;
        try {
            wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("比对结果");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "原始名称", "匹配名称", "相似度", "差异类型"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < list.size(); i++) {
                Map<String, Object> item = list.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(getStr(item, "name"));
                row.createCell(2).setCellValue(getStr(item, "matchedName"));
                row.createCell(3).setCellValue(getDouble(item, "similarity"));
                row.createCell(4).setCellValue(getStr(item, "diffType"));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=比对结果.xlsx");
            out = response.getOutputStream();
            wb.write(out);
            out.flush();
        } catch (IOException e) {
            logger.error("Excel导出IO写入异常", e);
        } finally {
            // 单独捕获close抛出的IOException，彻底解决编译报错
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.error("输出流关闭IO异常", e);
                }
            }
            if (wb != null) {
                try {
                    wb.close();
                } catch (IOException e) {
                    logger.error("工作簿关闭IO异常", e);
                }
            }
        }
    }

    // ===================== Excel读取 =====================
    private List<String> readFirstColumnNames(MultipartFile file) throws IOException {
        List<String> res = new ArrayList<>();
        Workbook wb = WorkbookFactory.create(file.getInputStream());
        try {
            Sheet sheet = wb.getSheetAt(0);
            boolean skipHead = true;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(0);
                if (cell == null) continue;
                String val = getCellValue(cell).trim();
                if (val.isEmpty()) continue;
                if (skipHead) {
                    skipHead = false;
                    if (val.contains("名称") || val.contains("单位") || val.equalsIgnoreCase("name")) continue;
                }
                res.add(val);
            }
        } finally {
            wb.close();
        }
        return res;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                double num = cell.getNumericCellValue();
                return num == Math.floor(num) ? String.valueOf((long) num) : String.valueOf(num);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    // ===================== Ollama向量（更新进度） =====================
    private Map<String, double[]> batchEmbedding(List<String> textList, CompareProgress progress) {
        Map<String, double[]> map = new HashMap<>();
        for (String text : textList) {
            progress.currentText = text;
            try {
                double[] vec = getSingleEmbedding(text);
                map.put(text, vec);
            } catch (Exception e) {
                logger.error("文本向量生成失败:{}", text, e);
            }
        }
        return map;
    }

    private double[] getSingleEmbedding(String text) throws IOException {
        JSONObject req = new JSONObject();
        req.put("model", EMBED_MODEL);
        req.put("prompt", text);
        RequestBody body = RequestBody.create(req.toString(), JSON_MEDIA);
        Request request = new Request.Builder()
                .url(OLLAMA_EMBED_URL)
                .post(body)
                .build();
        try (okhttp3.Response resp = HTTP_CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) throw new IOException("Ollama 异常，状态码：" + resp.code());
            JSONObject json = JSON.parseObject(resp.body().string());
            List<Double> embed = json.getList("embedding", Double.class);
            double[] arr = new double[embed.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = embed.get(i);
            return arr;
        }
    }

    // 余弦相似度
    private double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += Math.pow(a[i], 2);
            nb += Math.pow(b[i], 2);
        }
        double norm = Math.sqrt(na) * Math.sqrt(nb);
        return norm == 0 ? 0 : dot / norm;
    }

    // ===================== 比对主逻辑（更新进度） =====================
    private List<Map<String, Object>> matchProcess(
            List<String> originTexts,
            List<String> newTexts,
            Map<String, double[]> originVecMap,
            CompareProgress progress
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        boolean[] matchedFlag = new boolean[newTexts.size()];
        Map<String, double[]> newVecCache = batchEmbedding(newTexts, progress);

        for (String originText : originTexts) {
            progress.currentText = originText;
            double[] oVec = originVecMap.get(originText);
            int bestIdx = -1;
            double maxSim = 0;

            for (int i = 0; i < newTexts.size(); i++) {
                String nt = newTexts.get(i);
                double[] nVec = newVecCache.get(nt);
                if (nVec == null) continue;
                double sim = cosine(oVec, nVec);
                if (sim > maxSim) {
                    maxSim = sim;
                    bestIdx = i;
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", originText);
            item.put("originVal", originText);
            if (bestIdx == -1) {
                item.put("matchedName", "");
                item.put("newVal", "");
                item.put("similarity", 0.0);
                item.put("diffType", "未匹配");
            } else {
                String bestText = newTexts.get(bestIdx);
                if (originText.equalsIgnoreCase(bestText.trim())) {
                    matchedFlag[bestIdx] = true;
                    item.put("matchedName", bestText);
                    item.put("newVal", bestText);
                    item.put("similarity", 1.0);
                    item.put("diffType", "完全匹配");
                } else if (maxSim >= SIMILARITY_THRESHOLD) {
                    matchedFlag[bestIdx] = true;
                    item.put("matchedName", bestText);
                    item.put("newVal", bestText);
                    item.put("similarity", Math.round(maxSim * 100) / 100.0);
                    item.put("diffType", "语义模糊匹配");
                } else {
                    item.put("matchedName", "");
                    item.put("newVal", "");
                    item.put("similarity", Math.round(maxSim * 100) / 100.0);
                    item.put("diffType", "未匹配");
                }
            }
            result.add(item);
            progress.done.incrementAndGet();
        }

        // 新增条目
        progress.currentText = "扫描新增条目";
        for (int i = 0; i < newTexts.size(); i++) {
            if (!matchedFlag[i]) {
                String nt = newTexts.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", "");
                item.put("originVal", "");
                item.put("matchedName", nt);
                item.put("newVal", nt);
                item.put("similarity", 0.0);
                item.put("diffType", "新增项");
                result.add(item);
            }
        }
        return result;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString();
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(getStr(map, key));
        } catch (Exception e) {
            return 0;
        }
    }
}