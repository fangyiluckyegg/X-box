package com.prj.service.embedding.impl;

import com.prj.exception.EmbeddingException;
import com.prj.service.embedding.IEmbeddingService;
import com.prj.service.embedding.IOllamaEmbedClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Ollama 的文本向量化实现。
 *
 * <p>仅依赖 {@link IOllamaEmbedClient} 接口（可 mock），不依赖具体 HTTP 实现，
 * 故单元测试无需 mockito-inline 即可覆盖「正常 + 4 类异常」场景。
 *
 * <p>编排：将 texts 按 {@code EMBED_BATCH_SIZE=100} 分块，逐块调底层 client；
 * 对返回做数量/空向量校验（不符则计入 failCount）；捕获底层 {@link EmbeddingException} 累计 failCount + 记录首个分类。
 * 循环结束若 {@code failCount>0}：有成功批次则抛 {@link EmbeddingException.Category#PARTIAL}，否则原样抛出所记录分类的异常（保留 cause）。
 */
@Service
public class OllamaEmbeddingServiceImpl implements IEmbeddingService
{
    private static final Logger logger = LoggerFactory.getLogger(OllamaEmbeddingServiceImpl.class);

    /** 单次批量嵌入请求携带的文本条数上限（分块大小）。 */
    private static final int EMBED_BATCH_SIZE = 100;

    private final IOllamaEmbedClient embedClient;

    public OllamaEmbeddingServiceImpl(IOllamaEmbedClient embedClient)
    {
        this.embedClient = embedClient;
    }

    @Override
    public Map<String, double[]> embed(List<String> texts) throws EmbeddingException
    {
        Map<String, double[]> map = new LinkedHashMap<>();
        if (texts == null || texts.isEmpty())
        {
            return map;
        }
        int total = texts.size();
        int failCount = 0;
        int successCount = 0;
        EmbeddingException firstFailure = null;

        for (int start = 0; start < total; start += EMBED_BATCH_SIZE)
        {
            int end = Math.min(total, start + EMBED_BATCH_SIZE);
            List<String> chunk = new ArrayList<>(texts.subList(start, end));
            try
            {
                List<double[]> vectors = embedClient.embedBatch(chunk);
                // 数量校验：底层应保证返回与输入等长，不符即视为空响应异常
                if (vectors == null || vectors.size() != chunk.size())
                {
                    throw new EmbeddingException(EmbeddingException.Category.EMPTY,
                            "Ollama 返回向量数量(" + (vectors == null ? "null" : vectors.size())
                                    + ")与输入数量(" + chunk.size() + ")不一致");
                }
                for (int i = 0; i < chunk.size(); i++)
                {
                    double[] vec = vectors.get(i);
                    if (vec == null || vec.length == 0)
                    {
                        failCount++;
                        logger.error("文本向量生成失败(返回空向量):{}", chunk.get(i));
                    }
                    else
                    {
                        map.put(chunk.get(i), vec);
                        successCount++;
                    }
                }
            }
            catch (EmbeddingException e)
            {
                failCount += chunk.size();
                logger.error("批量向量生成失败:{}~{}", start + 1, end, e);
                if (firstFailure == null)
                {
                    firstFailure = e;
                }
            }
        }

        if (failCount > 0)
        {
            if (successCount > 0)
            {
                // 部分成功 → 整体无法完成，提示重试
                throw new EmbeddingException(EmbeddingException.Category.PARTIAL,
                        "部分文本向量化失败，请重试（成功 " + successCount + "/" + total + " 条）");
            }
            // 完全失败：原样抛出所记录的首个分类异常（保留 cause 与 message）
            if (firstFailure != null)
            {
                throw firstFailure;
            }
            throw new EmbeddingException(EmbeddingException.Category.EMPTY,
                    "向量化失败 " + failCount + "/" + total + " 条，未返回有效向量");
        }
        return map;
    }
}
