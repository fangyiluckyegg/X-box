package com.prj.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Excel 双文件比对控制器（Compare）。
 *
 * <p>职责：
 * 提供基于语义向量的 Excel 名称/条目比对能力：
 * 1. 接收"原始 Excel"与"比对 Excel"，读取各自首列条目；
 * 2. 调用 Ollama（bge-m3 模型）将文本转为向量，用余弦相似度匹配；
 * 3. 区分"完全匹配 / 语义模糊匹配 / 未匹配 / 新增项"，结果按用户缓存；
 * 4. 提供实时进度查询接口（前端轮询进度条）与结果 Excel 导出接口。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code SecurityUtils}（获取当前登录用户名，作为缓存 key）、
 *         Apache POI（Excel 读写）、OkHttp（调用 Ollama embeddings 接口）、
 *         fastjson2（JSON 解析）。
 * - 被依赖：前端比对页面（web/prj-frontend/src/views/compare）轮询 /progress、提交 /compare、下载 /downloadResult。
 *
 * <p>架构说明：比对结果缓存（RESULT_CACHE）与进度缓存（PROGRESS_CACHE）均以"登录用户名"为 key，
 * 存放在静态 ConcurrentHashMap 中，5 分钟后由后台线程回收（修复了原实现仅回收进度、导致结果 Map 内存泄漏的问题）。
 * 向量服务地址与模型名已实现配置化（[TASK-⑥]）：通过 {@code @Value} 读取环境变量
 * AI_SERVICE_URL（基础地址，缺省 http://dev-prj-llama:11434）/ AI_EMBED_MODEL（模型名，缺省 bge-m3:latest），
 * 不再硬编码，由容器编排或 .env 注入。批量嵌入端点改为 Ollama 的 /api/embed（[TASK-⑤]），
 * 以 input 数组一次请求获取多条向量，替代原先逐条调用 /api/embeddings 的串行方式，显著提升向量化速度。
 */
/**
 * Excel双文件比对控制器
 * 新增实时进度查询接口，前端轮询展示进度条
 * 修复：导出Excel close() IO异常捕获，无编译报错
 */
@Validated
@RestController
@RequestMapping("/api/excel")
public class CompareController {

    /** 类级日志对象。 */
    private static final Logger logger = LoggerFactory.getLogger(CompareController.class);

    // 比对结果缓存 key=登录用户名
    /** 比对结果缓存：key=登录用户名，value=比对结果列表（每条为一个 Map）。 */
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> RESULT_CACHE = new ConcurrentHashMap<>();
    // 用户比对进度缓存 key=登录用户名
    /** 比对进度缓存：key=登录用户名，value=当前用户的进度对象（线程安全）。 */
    private static final ConcurrentHashMap<String, CompareProgress> PROGRESS_CACHE = new ConcurrentHashMap<>();

    /**
     * [P3] 共享的缓存清理调度线程池（单例、守护线程）。
     * 替代原 {@code compareExcel} 中每请求派生 {@code new Thread().start()} 的做法，
     * 避免高并发下线程堆积、且无需额外 JVM 关闭钩子（守护线程随 JVM 退出而终止）。
     */
    private static final ScheduledExecutorService CLEANUP_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "compare-cache-cleaner");
                t.setDaemon(true);
                return t;
            });

    // ===================== 进度实体（线程安全）=====================
    /**
     * 比对进度实体（线程安全）。
     *
     * <p>用于在比对执行过程中记录总条数、已完成条数、当前处理文本与所处阶段，
     * 并由前端通过 /progress 接口轮询读取。各字段采用原子/易变修饰以保证多线程可见性与原子更新。
     */
    public static class CompareProgress {
        /** 总条目数（原子整型）。 */
        public final AtomicInteger total = new AtomicInteger(0);
        /** 已完成条目数（原子整型，每处理一条 +1）。 */
        public final AtomicInteger done = new AtomicInteger(0);
        /** 当前正在处理的文本（易变，供前端展示）。 */
        public volatile String currentText = "";
        // vector_calc / match_compare / done
        /** 当前阶段标识：vector_calc（向量计算）/ match_compare（相似度匹配）/ done（完成）。 */
        public volatile String stage = "";

        /** 计算完成百分比（保留两位小数）。 */
        public double getPercent() {
            int t = total.get();
            int d = done.get();
            if (t <= 0) return 0.00;
            return Math.round((d * 100.0 / t) * 100) / 100.0;
        }

        /** 将进度序列化为 JSON 对象，供接口返回前端。 */
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
    /**
     * 查询当前用户的实时比对进度。
     *
     * @return 若无进行中任务返回"无任务"（data=null）；否则返回进度 JSON（total/done/percent/currentText/stage）
     */
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

    // ===================== 获取已完成比对结果 =====================
    /**
     * 获取当前用户最近一次已完成的比对结果（progress stage=done 后调用）。
     * 用于前端纯轮询架构：/compare 触发后不等待，轮询到 done 时调此接口取结果。
     *
     * @return 比对结果列表；无结果时返回空列表
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/fetchResult")
    public AjaxResult fetchResult() {
        String username = SecurityUtils.getUsername();
        List<Map<String, Object>> result = RESULT_CACHE.get(username);
        if (result == null || result.isEmpty()) {
            return AjaxResult.success("无比对结果", new ArrayList<>());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", result);
        return AjaxResult.success("获取成功", data);
    }

    // ===================== 向量服务配置（[TASK-⑥] 配置化，消除硬编码） =====================
    /**
     * Ollama 向量服务基础地址（容器编排内网服务名 dev-prj-llama）。
     * [TASK-⑥] 外部化配置：读取环境变量/配置项 AI_SERVICE_URL，缺省沿用原内网服务名。
     * 批量嵌入端点在此基础上拼接 /api/embed（见 {@link #getEmbedUrl()}）。
     */
    @Value("${AI_SERVICE_URL:http://dev-prj-llama:11434}")
    private String aiServiceBaseUrl;

    /**
     * 向量化模型名称。
     * [TASK-⑥] 外部化配置：读取环境变量/配置项 AI_EMBED_MODEL，缺省为 bge-m3:latest。
     */
    @Value("${AI_EMBED_MODEL:bge-m3:latest}")
    private String embedModel;

    /**
     * [TASK-⑤] 单次批量嵌入请求携带的文本条数上限（分块大小）。
     * 用 /api/embed 的 input 数组一次请求处理多条文本；超过该值则自动分块，
     * 兼顾单次请求体体积与读取超时，避免超大文件单请求过长导致超时。
     */
    private static final int EMBED_BATCH_SIZE = 100;

    /** 判定为"语义模糊匹配"的相似度阈值（>= 0.85）。 */
    private static final double SIMILARITY_THRESHOLD = 0.85;
    /** [FIX-③] 新数据向量化覆盖率下限（成功条数/总条数），低于此值视为向量化失败、不再静默产出退化结果。 */
    private static final double NEW_COVERAGE_THRESHOLD = 0.5;
    /** HTTP 请求体的 JSON 媒体类型。 */
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    /** 复用的 OkHttp 客户端（连接超时 30s / 读取超时 120s / 连接池 20 连接）。 */
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // [TASK-⑤] 批量嵌入单请求可能处理 EMBED_BATCH_SIZE 条文本，放宽读取超时到 120s 避免大批量超时
            .readTimeout(120, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(20, 30, TimeUnit.SECONDS))
            .build();

    /**
     * 开始比对主接口。
     *
     * @param originExcel 原始 Excel 文件（待比对基准）
     * @param newExcel    比对 Excel 文件（新版本/待核对版本）
     * @return 成功时返回比对结果列表；任一文件为空、全部向量生成失败或过程异常时返回对应错误响应
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
        System.out.println("[DIAG] compareExcel ENTER, username=" + username);
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

            // [FIX-③] 阶段1.5：新数据向量计算（与 origin 侧对称，提前到 matchProcess 之前以便校验覆盖率）
            progress.currentText = "计算新数据向量";
            Map<String, double[]> newVecMap;
            try {
                // [FIX-④] batchEmbedding 在任一单条失败时会抛出明确异常，此处捕获并给出面向用户的清晰报错
                newVecMap = batchEmbedding(newList, progress);
            } catch (RuntimeException re) {
                PROGRESS_CACHE.remove(username);
                return AjaxResult.error("新数据" + re.getMessage());
            }
            // [FIX-③] new 侧覆盖率校验：空或低于阈值则明确报错，避免进入 matchProcess 产出"全未匹配"退化结果
            if (!newList.isEmpty()) {
                int covered = newVecMap.size();
                int total = newList.size();
                if (newVecMap.isEmpty() || (double) covered / total < NEW_COVERAGE_THRESHOLD) {
                    PROGRESS_CACHE.remove(username);
                    return AjaxResult.error(String.format(
                            "新数据向量化失败：成功 %d/%d 条，未达阈值 %.2f", covered, total, NEW_COVERAGE_THRESHOLD));
                }
            }

            // 阶段2：相似度匹配
            progress.stage = "match_compare";
            List<Map<String, Object>> result = matchProcess(validOrigin, newList, originVecMap, newVecMap, progress);

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
            // [P3] 改为共享守护线程池调度，避免每请求派生线程（高并发下线程堆积）。
            CLEANUP_SCHEDULER.schedule(() -> {
                PROGRESS_CACHE.remove(username);
                RESULT_CACHE.remove(username);
            }, 5, TimeUnit.MINUTES);
        }
    }

    /**
     * 导出比对结果为 Excel 文件（已修复 IO 未捕获异常编译报错）。
     *
     * @param response HTTP 响应，写入 Excel 附件流；无比对结果时返回 JSON 错误提示
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
    /**
     * 读取 Excel 首列所有非空文本（自动跳过表头行）。
     *
     * @param file 上传的 Excel 文件
     * @return 首列有效文本列表（已去除首尾空白）
     * @throws IOException 文件流读取失败时抛出
     */
    /** [P4] Excel 读取行数硬上限（5万行），防止恶意/超大文件导致 OOM（DoS）。 */
    private static final int MAX_EXCEL_ROWS = 50_000;

    private List<String> readFirstColumnNames(MultipartFile file) throws IOException {
        List<String> res = new ArrayList<>();
        // [P4] zip bomb 防护：设置最小解压比阈值（默认 0.01，放宽到 0.005），
        // 过低比例的 OOXML 压缩包在读取时抛异常，避免解压膨胀耗尽内存。
        ZipSecureFile.setMinInflateRatio(0.005);
        Workbook wb = WorkbookFactory.create(file.getInputStream());
        try {
            Sheet sheet = wb.getSheetAt(0);
            // [P4] 行数上限防护：超过 MAX_EXCEL_ROWS 直接拒绝，避免 OOM（DoS）
            if (sheet.getLastRowNum() > MAX_EXCEL_ROWS) {
                throw new IllegalArgumentException("Excel 行数超过上限 " + MAX_EXCEL_ROWS);
            }
            boolean skipHead = true;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                // [P4] 双重保护：循环内再次校验当前行号，即便 LastRowNum 估算不准也不超量读取
                if (r > MAX_EXCEL_ROWS) {
                    throw new IllegalArgumentException("Excel 行数超过上限 " + MAX_EXCEL_ROWS);
                }
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(0);
                if (cell == null) continue;
                String val = getCellValue(cell).trim();
                if (val.isEmpty()) continue;
                // 跳过含义为表头的首行（含"名称"/"单位"/"name"字样）
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

    /**
     * 以字符串形式读取单元格内容（区分字符串/数值/布尔类型）。
     *
     * @param cell 目标单元格
     * @return 单元格的字符串值；空单元格或无法识别类型返回空串
     */
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

    // ===================== 向量服务（[TASK-⑤] 真批量 /api/embed） =====================
    /**
     * 由基础地址拼接 Ollama 批量嵌入端点 /api/embed。
     * [TASK-⑤] 端点固定为 /api/embed（批量接口），基础地址来自配置项 AI_SERVICE_URL。
     *
     * @return 完整的批量嵌入请求 URL
     */
    private String getEmbedUrl() {
        String base = (aiServiceBaseUrl == null) ? "" : aiServiceBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/embed";
    }

    /**
     * 对文本列表批量生成向量，并在过程中更新进度文本。
     * [TASK-⑤] 改串行 /api/embeddings 为真批量 /api/embed：将文本按 {@link #EMBED_BATCH_SIZE} 分块，
     * 每块以 input 数组一次请求获取多条向量，显著降低网络往返次数、提升向量化速度。
     *
     * @param textList 文本列表
     * @param progress 进度对象（用于写入当前处理文本）
     * @return key=文本、value=向量数组；与入参保持一致的映射关系（重复文本按各自条目映射）
     */
    private Map<String, double[]> batchEmbedding(List<String> textList, CompareProgress progress) {
        System.out.println("[DIAG] batchEmbedding ENTER, textList.size=" + (textList == null ? "null" : textList.size()));
        Map<String, double[]> map = new LinkedHashMap<>();
        if (textList == null || textList.isEmpty()) {
            return map;
        }
        int total = textList.size();
        int failCount = 0;
        // 分块批量调用 /api/embed，替代逐条 /api/embeddings 串行调用
        for (int start = 0; start < total; start += EMBED_BATCH_SIZE) {
            int end = Math.min(total, start + EMBED_BATCH_SIZE);
            List<String> chunk = new ArrayList<>(textList.subList(start, end));
            progress.currentText = "批量向量化 " + (start + 1) + "~" + end + " / " + total;
            try {
                List<double[]> vectors = callBatchEmbedApi(chunk);
                for (int i = 0; i < chunk.size(); i++) {
                    double[] vec = vectors.get(i);
                    // [FIX-④] 单条失败不再静默跳过：返回空（理论上不会，仅防御）也计入失败数
                    if (vec == null || vec.length == 0) {
                        failCount++;
                        logger.error("文本向量生成失败(返回空向量):{}", chunk.get(i));
                    } else {
                        map.put(chunk.get(i), vec);
                    }
                }
            } catch (Exception e) {
                // [FIX-④] 整块失败：该块全部计入失败数，循环结束后统一抛出
                failCount += chunk.size();
                logger.error("批量向量生成失败:{}~{}", start + 1, end, e);
            }
        }
        // [FIX-④] 循环结束后：任一单条失败即视为整体向量化失败，向上抛明确异常，
        // 由调用方（③ 守卫）给出明确报错，而非继续产出"全未匹配、相似度0"的退化结果。
        if (failCount > 0) {
            throw new RuntimeException("向量化失败 " + failCount + "/" + total + " 条");
        }
        return map;
    }

    /**
     * 调用 Ollama 批量嵌入接口 /api/embed，一次请求将多条文本转为向量。
     * [TASK-⑤] 使用 input 数组（而非单条 prompt），响应 embeddings 为与 input 等长的二维数组。
     *
     * @param texts 本批次文本列表（非空）
     * @return 与入参顺序一一对应的向量数组列表
     * @throws IOException 网络请求失败、Ollama 返回非成功状态码或向量数量不匹配时抛出
     */
    private List<double[]> callBatchEmbedApi(List<String> texts) throws IOException {
        String embedUrl = getEmbedUrl();
        System.out.println("[DIAG] callBatchEmbedApi ENTER, texts.size=" + texts.size() + ", url=" + embedUrl + ", model=" + embedModel);
        JSONObject req = new JSONObject();
        req.put("model", embedModel);
        req.put("input", texts);
        RequestBody body = RequestBody.create(req.toString(), JSON_MEDIA);
        Request request = new Request.Builder()
                .url(embedUrl)
                .post(body)
                .build();
        System.out.println("[DIAG] callBatchEmbedApi sending HTTP request...");
        try (okhttp3.Response resp = HTTP_CLIENT.newCall(request).execute()) {
            int code = resp.code();
            String rawBody = (resp.body() != null) ? resp.body().string() : "";
            // 安全截断预览（contentLength 在 chunked 传输时返回 -1）
            String bodyPreview = (rawBody.length() > 200) ? rawBody.substring(0, 200) + "..." : rawBody;
            System.out.println("[DIAG] callBatchEmbedApi response code=" + code + ", body_preview=" + bodyPreview);
            if (!resp.isSuccessful()) throw new IOException("Ollama 异常，状态码：" + code);
            JSONObject json = JSON.parseObject(rawBody);
            // /api/embed 的响应字段为 embeddings（二维数组，与 input 顺序一致）
            JSONArray embeds = json.getJSONArray("embeddings");
            // [FIX-④] Ollama 返回无 embeddings 字段（如 404/500 错误体）时 getJSONArray 返回 null，显式抛异常
            if (embeds == null || embeds.isEmpty()) {
                throw new IOException("Ollama 未返回向量(embeddings 为空)");
            }
            if (embeds.size() != texts.size()) {
                throw new IOException("Ollama 返回向量数量(" + embeds.size()
                        + ")与输入数量(" + texts.size() + ")不一致");
            }
            System.out.println("[DIAG] callBatchEmbedApi SUCCESS, embeddings count=" + embeds.size() + ", dim=" + (embeds.isEmpty() ? 0 : embeds.getJSONArray(0).size()));
            List<double[]> result = new ArrayList<>(embeds.size());
            for (int k = 0; k < embeds.size(); k++) {
                JSONArray e = embeds.getJSONArray(k);
                if (e == null || e.isEmpty()) {
                    throw new IOException("Ollama 返回某条向量为空");
                }
                double[] arr = new double[e.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = e.getDoubleValue(i);
                }
                result.add(arr);
            }
            return result;
        }
    }

    // 余弦相似度
    /**
     * 计算两个向量的余弦相似度（取值 0~1，越接近 1 越相似）。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 余弦相似度；任一侧为零向量时返回 0
     */
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
    /**
     * 比对主逻辑：对原始文本逐条在比对文本中寻找最相似项，并标记未匹配与新增项。
     *
     * @param originTexts  原始文本列表（基准）
     * @param newTexts     比对文本列表（新版本）
     * @param originVecMap 原始文本->向量映射
     * @param progress     进度对象（更新 currentText 与 done 计数）
     * @return 比对结果列表，每条为含 name/originVal/matchedName/newVal/similarity/diffType 的 Map
     */
    private List<Map<String, Object>> matchProcess(
            List<String> originTexts,
            List<String> newTexts,
            Map<String, double[]> originVecMap,
            Map<String, double[]> newVecMap,
            CompareProgress progress
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        boolean[] matchedFlag = new boolean[newTexts.size()];
        // [FIX-③] new 侧向量由调用方（compareExcel）预先计算并完成覆盖率校验后传入，
        // 此处不再内部调用 batchEmbedding，避免 new 侧失败被静默吞掉、产出"全未匹配"退化结果。
        Map<String, double[]> newVecCache = newVecMap;

        for (String originText : originTexts) {
            progress.currentText = originText;
            double[] oVec = originVecMap.get(originText);
            int bestIdx = -1;
            double maxSim = 0;

            // 在比对文本中找与当前原始文本余弦相似度最高的项
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
                // 文本完全一致 -> 完全匹配（相似度记 1.0）
                if (originText.equalsIgnoreCase(bestText.trim())) {
                    matchedFlag[bestIdx] = true;
                    item.put("matchedName", bestText);
                    item.put("newVal", bestText);
                    item.put("similarity", 1.0);
                    item.put("diffType", "完全匹配");
                // 相似度达到阈值 -> 语义模糊匹配
                } else if (maxSim >= SIMILARITY_THRESHOLD) {
                    matchedFlag[bestIdx] = true;
                    item.put("matchedName", bestText);
                    item.put("newVal", bestText);
                    item.put("similarity", Math.round(maxSim * 100) / 100.0);
                    item.put("diffType", "语义模糊匹配");
                // 低于阈值 -> 视为未匹配
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

        // 新增条目：遍历比对文本，凡未被任何原始文本匹配上的，记为"新增项"
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

    /**
     * 从结果 Map 中按 key 安全取出字符串值。
     *
     * @param map 结果 Map
     * @param key 字段名
     * @return 字符串值，null 时返回空串
     */
    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString();
    }

    /**
     * 从结果 Map 中按 key 安全取出 double 值（兼容 Number 与可解析字符串）。
     *
     * @param map 结果 Map
     * @param key 字段名
     * @return double 值，无法解析时返回 0
     */
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
