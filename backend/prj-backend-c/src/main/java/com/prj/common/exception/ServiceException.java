package com.prj.common.exception;

/**
 * 业务异常（运行时异常）。
 *
 * <p>职责：
 * 统一表达业务层的"预期内"错误（如用户不存在、文件类型不支持等），携带错误码、提示信息与调试明细。
 * 由全局异常处理器 {@code GlobalExceptionHandler} 捕获并转换为统一响应。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code UserDetailsServiceImpl}、{@code UploadServiceImpl} 等在各业务场景抛出；
 *           {@code GlobalExceptionHandler} 负责兜底转换。
 *
 * <p>设计：提供链式 {@code setMessage/setDetailMessage}，便于在抛出前补充上下文。
 */
public final class ServiceException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**     * 错误码     */
    private Integer code;
    /**     * 错误提示     */
    private String message;
    /**     * 错误明细，内部调试错误     *
     * 和 {@link CommonResult#getDetailMessage()} 一致的设计
     */
    private String detailMessage;

    /**     * 空构造方法，避免反序列化问题     */
    public ServiceException()
    {
    }

    /** 仅携带提示信息构造。 */
    public ServiceException(String message)
    {
        this.message = message;
    }
    /** 携带提示信息与错误码构造。 */
    public ServiceException(String message, Integer code)
    {
        this.message = message;
        this.code = code;
    }

    /** 获取调试明细。 */
    public String getDetailMessage()
    {
        return detailMessage;
    }

    /** 获取提示信息。 */
    public String getMessage()
    {
        return message;
    }

    /** 获取错误码。 */
    public Integer getCode()
    {
        return code;
    }

    /** 链式设置提示信息，返回自身。 */
    public ServiceException setMessage(String message)
    {
        this.message = message;
        return this;
    }

    /** 链式设置调试明细，返回自身。 */
    public ServiceException setDetailMessage(String detailMessage)
    {
        this.detailMessage = detailMessage;
        return this;
    }
}
