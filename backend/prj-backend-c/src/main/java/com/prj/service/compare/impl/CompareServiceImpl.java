package com.prj.service.compare.impl;

import com.prj.exception.EmbeddingException;
import com.prj.service.compare.ExcelReader;
import com.prj.service.compare.ICompareService;
import com.prj.service.embedding.IEmbeddingService;
import com.prj.service.similarity.ISimilarityService;
import com.prj.store.IProgressStore;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/**
 * 比对编排实现（异步 worker）。
 *
 * <p>由 CompareController 在请求线程内调度，经 {@code @Async("compareTaskExecutor")} 在独立线程池执行，
 * 全程经 {@code IProgressStore} 回写进度/结果；任意异常 → {@code markFailed(username, 明确message)}，
 * 前端轮询拿到 stage=failed 后红色提示 + 重试。
 *
 * <p>编排顺序：saveProgress(uploaded) → ExcelReader.read → saveProgress(vector_calc)
 * → embed(origin) → embed(new)（new 侧覆盖率 < 阈值则 markFailed 并 return）
 * → saveProgress(match_compare) → similarity.compare → saveResult → saveProgress(done,100%)。
 */
@Service
public class CompareServiceImpl implements ICompareService
{
    private static final Logger logger = LoggerFactory.getLogger(CompareServiceImpl.class);

    /** 新数据向量化覆盖率下限（成功条数/总条数），低于此值视为向量化失败、不再静默产出退化结果。 */
    private static final double NEW_COVERAGE_THRESHOLD = 0.5;

    /** 单批次向量化条数（与 EMBED_BATCH_SIZE 对齐，仅用于进度文本展示）。 */
    private static final int EMBED_STEP = 100;

    /** 异常分类 → 用户可见 message 映射（与 EmbeddingException.Category 对齐）。 */
    private static final Map<EmbeddingException.Category, String> CATEGORY_MESSAGE = Map.of(
            EmbeddingException.Category.TIMEOUT, "Ollama 超时，请检查向量服务",
            EmbeddingException.Category.CONNECTION, "Ollama 连接失败，请检查向量服务",
            EmbeddingException.Category.EMPTY, "向量服务返回空结果",
            EmbeddingException.Category.PARTIAL, "部分文本向量化失败，请重试"
    );

    private final IEmbeddingService embeddingService;
    private final ISimilarityService similarityService;
    private final IProgressStore progressStore;
    private final ExcelReader excelReader;

    public CompareServiceImpl(IEmbeddingService embeddingService,
                              ISimilarityService similarityService,
                              IProgressStore progressStore,
                              ExcelReader excelReader)
    {
        this.embeddingService = embeddingService;
        this.similarityService = similarityService;
        this.progressStore = progressStore;
        this.excelReader = excelReader;
    }

    @Async("compareTaskExecutor")
    @Override
    public void performCompare(String username, byte[] originExcel, byte[] newExcel)
    {
        try
        {
            // 阶段0：已上传（复用 Controller 预置的 uploaded 占位进度，避免覆盖丢失）
            ProgressVo progress = progressStore.getProgress(username);
            if (progress == null)
            {
                progress = new ProgressVo();
            }
            progress.setStage("uploaded");
            progress.setPercent(0);
            progress.setCurrent(0);
            progress.setTotal(0);
            progress.setCurrentText("文件已上传，开始读取");
            progressStore.saveProgress(username, progress);

            // 读取 Excel 首列（入参为请求线程内已读出的字节数组，包装为 ByteArrayInputStream，
            // 不再依赖 Tomcat 上传临时文件——避免 202 返回后临时文件被清理导致 getInputStream 抛 IOException）
            List<String> originList = excelReader.readFirstColumnNames(new ByteArrayInputStream(originExcel));
            List<String> newList = excelReader.readFirstColumnNames(new ByteArrayInputStream(newExcel));

            if (originList.isEmpty())
            {
                progressStore.markFailed(username, "原始文件无有效数据");
                return;
            }

            // 阶段1：向量计算（origin）
            progress.setStage("vector_calc");
            progress.setTotal(originList.size());
            progress.setCurrent(0);
            progress.setCurrentText("计算原始数据向量 1~" + Math.min(EMBED_STEP, originList.size()) + " / " + originList.size());
            progressStore.saveProgress(username, progress);

            Map<String, double[]> originVecMap = embeddingService.embed(originList);
            if (originVecMap.isEmpty())
            {
                progressStore.markFailed(username, "全部向量生成失败，请检查Ollama容器");
                return;
            }

            // 阶段1.5：新数据向量计算（与 origin 侧对称），并校验覆盖率
            progress.setCurrentText("计算新数据向量");
            progressStore.saveProgress(username, progress);
            Map<String, double[]> newVecMap;
            try
            {
                newVecMap = embeddingService.embed(newList);
            }
            catch (EmbeddingException e)
            {
                progressStore.markFailed(username, "新数据向量化失败：" + resolveMessage(e));
                return;
            }
            if (!newList.isEmpty())
            {
                int covered = newVecMap.size();
                int total = newList.size();
                if (newVecMap.isEmpty() || (double) covered / total < NEW_COVERAGE_THRESHOLD)
                {
                    progressStore.markFailed(username, String.format(
                            "新数据向量化失败：成功 %d/%d 条，未达阈值 %.2f", covered, total, NEW_COVERAGE_THRESHOLD));
                    return;
                }
            }

            // 阶段2：相似度匹配
            progress.setStage("match_compare");
            progress.setCurrent(0);
            progress.setCurrentText("开始相似度匹配比对");
            progressStore.saveProgress(username, progress);

            List<CompareResultRow> result = similarityService.compare(originList, newList, originVecMap, newVecMap, progress);

            // 完成
            progressStore.saveResult(username, result);
            progress.setStage("done");
            progress.setPercent(100);
            progress.setCurrent(originList.size());
            progress.setTotal(originList.size());
            progress.setCurrentText("比对完成");
            progressStore.saveProgress(username, progress);
        }
        catch (EmbeddingException e)
        {
            logger.error("比对向量化异常 username={}", username, e);
            progressStore.markFailed(username, resolveMessage(e));
        }
        catch (Exception e)
        {
            logger.error("比对异常 username={}", username, e);
            String detail = (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage();
            progressStore.markFailed(username, "比对失败：" + detail);
        }
    }

    /** 将 EmbeddingException 分类映射为用户可见 message（未知分类兜底）。 */
    private String resolveMessage(EmbeddingException e)
    {
        return CATEGORY_MESSAGE.getOrDefault(e.getCategory(), "向量化失败：" + e.getMessage());
    }
}
