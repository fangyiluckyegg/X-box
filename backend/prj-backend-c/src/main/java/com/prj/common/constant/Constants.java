package com.prj.common.constant;

/**
 * 全局常量定义类。
 *
 * <p>职责：
 * 集中存放项目中通用的字符串/数值常量，主要包括统一响应字段名、Redis 缓存 key 前缀与过期时间、
 * Token 前缀等，避免在业务代码中散落硬编码字面量。
 *
 * <p>与其他模块的关联：
 * - 被依赖：Controller（如 LoginController 用 TOKEN）、Service、CaptchaController、TokenService 等
 *           通过本类引用缓存 key 与过期时间，保证 key 命名与生命周期统一。
 */
public class Constants
{
    /** 字符编码常量。 */
    public static final String UTF8 = "UTF-8";
    /** 验证码缓存 key 前缀（完整 key = 前缀 + uuid）。 */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";
    /** 登录 token 缓存 key 前缀（完整 key = 前缀 + token）。 */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";
    /** 验证码有效期（单位：分钟，配合 TimeUnit.MINUTES 使用）。 */
    public static final Integer CAPTCHA_EXPIRATION = 2;
    /** 响应体中 token 字段名。 */
    public static final String TOKEN = "token";
    /** HTTP Authorization 头中 Bearer Token 的前缀。 */
    public static final String TOKEN_PREFIX = "Bearer ";
    /** 登录用户 token 在请求属性/上下文中的 key。 */
    public static final String LOGIN_USER_TOKEN_KEY = "login_user_token_key";
}
