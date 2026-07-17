package com.prj.exception;

/**
 * 向量化业务异常。
 *
 * <p>封装失败分类（{@link Category}），便于调用方据此向用户上报明确的错误提示，
 * 并经 {@code IProgressStore.markFailed} 写入失败原因（{@code ProgressVo.message}）。
 *
 * <p>与其他模块的关联：
 * - 抛出方：{@code OllamaEmbeddingServiceImpl}（按批次失败归类）、{@code OkHttpOllamaEmbedClient}（按 HTTP/IO 异常归类）。
 * - 捕获方：{@code CompareServiceImpl.performCompare}（异步编排），将分类映射为用户可见 message 写入进度存储。
 */
public class EmbeddingException extends RuntimeException
{
    /** 失败分类枚举。 */
    public enum Category
    {
        /** 向量服务调用超时（连接超时或读取超时）。 */
        TIMEOUT,
        /** 向量服务连接失败（如连接被拒绝、DNS 不可达）。 */
        CONNECTION,
        /** 向量服务返回空结果（HTTP 200 但无 embeddings，或返回数量与输入不符）。 */
        EMPTY,
        /** 部分文本向量化失败（存在成功批次，整体无法完成，需重试）。 */
        PARTIAL
    }

    private final Category category;

    public EmbeddingException(Category category, String message, Throwable cause)
    {
        super(message, cause);
        this.category = category;
    }

    public EmbeddingException(Category category, String message)
    {
        super(message);
        this.category = category;
    }

    /** 失败分类（供调用方映射用户可见 message）。 */
    public Category getCategory()
    {
        return category;
    }
}
