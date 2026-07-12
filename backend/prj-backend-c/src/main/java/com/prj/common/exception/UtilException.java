package com.prj.common.exception;


/**
 * 工具类/通用工具异常（运行时异常）。
 *
 * <p>职责：
 * 表达通用工具方法执行中的异常（如编解码、解析失败），支持仅消息、仅原因、或两者兼备的构造形式。
 *
 * <p>与其他模块的关联：
 * - 被依赖：各工具类（如编解码、ID 生成等）在出错时抛出。
 */
public class UtilException extends RuntimeException
{
    private static final long serialVersionUID = 8247610319171014183L;

    /** 基于已有异常构造（沿用其消息与原因链）。 */
    public UtilException(Throwable e)
    {
        super(e.getMessage(), e);
    }

    /** 仅携带消息构造。 */
    public UtilException(String message)
    {
        super(message);
    }

    /** 携带消息与原因异常构造。 */
    public UtilException(String message, Throwable throwable)
    {
        super(message, throwable);
    }
}
