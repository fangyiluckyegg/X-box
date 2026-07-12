package com.prj.common.utils.uuid;

/**
 * UUID 便捷工具类。
 *
 * <p>职责：
 * 对自定义 {@link UUID} 类的简单封装，提供"无横线简单 UUID"与"快速 UUID"两种常用生成方式。
 *
 * <p>与其他模块的关联：
 * - 依赖：本包下的 {@link UUID}（项目自带的 UUID 实现，支持是否安全随机）。
 * - 被依赖：{@code CaptchaController}（生成验证码 uuid 标识）。
 */
public class IdUtils
{

    /** 生成不带横线的简单 UUID 字符串。 */
    public static String simpleUUID()
    {
        return UUID.randomUUID().toString(true);
    }

    /** 生成快速（非安全随机数）UUID 字符串。 */
    public static String fastUUID()
    {
        return UUID.fastUUID().toString();
    }
}
