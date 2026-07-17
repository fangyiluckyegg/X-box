package com.prj.service.embedding;

import com.prj.exception.EmbeddingException;

import java.util.List;
import java.util.Map;

/**
 * 文本向量化服务接口。
 *
 * <p>实现：{@link com.prj.service.embedding.impl.OllamaEmbeddingServiceImpl}（分块 + 异常分类）。
 */
public interface IEmbeddingService
{
    /**
     * 整段文本向量化，按 EMBED_BATCH_SIZE=100 自动分块；任一失败抛 {@link EmbeddingException}（含分类）。
     *
     * @param texts 待向量化文本列表
     * @return key=文本、value=向量数组 的映射（与入参保持顺序一致）
     * @throws EmbeddingException 向量化失败（TIMEOUT / CONNECTION / EMPTY / PARTIAL）
     */
    Map<String, double[]> embed(List<String> texts) throws EmbeddingException;
}
