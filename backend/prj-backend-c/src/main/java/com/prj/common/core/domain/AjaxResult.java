package com.prj.common.core.domain;

import java.util.HashMap;

/**
 * 统一接口响应结果（已标记 @Deprecated，建议逐步迁移到新响应类，但当前仍被广泛使用）。
 *
 * <p>职责：
 * 继承 {@link HashMap}，以键值对方式承载接口返回数据，约定固定字段：
 * code（状态码）/ msg（提示信息）/ data（业务数据）。提供 success/error 一系列静态工厂方法。
 *
 * <p>与其他模块的关联：
 * - 被依赖：所有 Controller 的返回类型（如 BaseController、CaptchaController、CompareController 等）。
 * - 约定：成功 code=200，失败 code=500（error(int,String) 可自定义 code）。
 *
 * <p>注意：因继承 HashMap，所有 put 均返回 this（链式调用支持，见 {@link #put}）。
 */
@Deprecated
public class AjaxResult extends HashMap<String, Object>
{
    private static final long serialVersionUID = 1L;

    /** 状态码字段名。 */
    public static final String CODE_TAG = "code";
    /** 返回内容字段名。 */
    public static final String MSG_TAG = "msg";
    /** 数据对象字段名。 */
    public static final String DATA_TAG = "data";

    /** 默认构造（空结果）。 */
    public AjaxResult()
    {
    }

    /** 构造仅含状态码与消息的结果。 */
    public AjaxResult(int code, String msg)
    {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }
    /** 构造含状态码、消息与数据的结果。 */
    public AjaxResult(int code, String msg, Object data)
    {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        super.put(DATA_TAG, data);
    }

    /** 成功（默认消息"操作成功"）。 */
    public static AjaxResult success()
    {
        return AjaxResult.success("操作成功");
    }
    /** 成功（携带数据）。 */
    public static AjaxResult success(Object data)
    {
        return AjaxResult.success("操作成功", data);
    }

    /** 成功（自定义消息，无数据）。 */
    public static AjaxResult success(String msg)
    {
        return AjaxResult.success(msg, null);
    }
    /** 成功（自定义消息与数据，code 固定 200）。 */
    public static AjaxResult success(String msg, Object data)
    {
        return new AjaxResult(200, msg, data);
    }

    /** 失败（默认消息"操作失败"）。 */
    public static AjaxResult error()
    {
        return AjaxResult.error("操作失败");
    }
    /** 失败（自定义消息，无数据）。 */
    public static AjaxResult error(String msg)
    {
        return AjaxResult.error(msg, null);
    }

    /** 失败（自定义消息与数据，code 固定 500）。 */
    public static AjaxResult error(String msg, Object data)
    {
        return new AjaxResult(500, msg, data);
    }
    /** 失败（自定义状态码与消息，无数据）。 */
    public static AjaxResult error(int code, String msg)
    {
        return new AjaxResult(code, msg, null);
    }

    /** 链式写入字段，返回当前对象自身（支持连续 put）。 */
    @Override
    public AjaxResult put(String key, Object value)
    {
        super.put(key, value);
        return this;
    }
}
