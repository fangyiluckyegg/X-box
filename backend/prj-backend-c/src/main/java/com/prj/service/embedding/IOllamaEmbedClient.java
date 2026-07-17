package com.prj.service.embedding;

import com.prj.exception.EmbeddingException;

import java.util.List;

/**
 * 底层 Ollama 向量化调用缝（可注入接口）。
 *
 * <p>将真实的 HTTP 调用（OkHttp 调 Ollama {@code /api/embed}）与上层业务解耦，
 * 便于单元测试以 Mockito mock 本接口，覆盖超时/连接失败/空响应等异常场景（无需 mockito-inline）。
 *
 * <p>实现：{@link com.prj.service.embedding.impl.OkHttpOllamaEmbedClient}（真实 HTTP）。
 */
public interface IOllamaEmbedClient
{
    /**
     * 调 Ollama /api/embed 对单批次（≤EMBED_BATCH_SIZE）文本向量化。
     *
     * @param texts 本批次文本列表（非空）
     * @return 与 texts 顺序一致的向量数组列表，长度必须等于 texts.size()
     * @throws EmbeddingException 分类：TIMEOUT（超时）/ CONNECTION（连接失败）/ EMPTY（200 但空/数量不符）
     */
    List<double[]> embedBatch(List<String> texts);
}
