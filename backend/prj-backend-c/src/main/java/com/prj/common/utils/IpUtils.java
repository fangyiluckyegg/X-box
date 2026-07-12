package com.prj.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 工具。
 * <p>
 * [C8/C12] 统一从请求中解析真实客户端 IP，优先顺序：
 * <ol>
 *   <li>{@code X-Forwarded-For}（取第一个非unknown的IP，Nginx 单跳场景即真实客户端）</li>
 *   <li>{@code X-Real-IP}</li>
 *   <li>{@code request.getRemoteAddr()}</li>
 * </ol>
 * 依赖 {@code server.forward-headers-strategy=native} 开启，仅信任前置 Nginx 这一跳，避免 IP 伪造。
 * 凡需客户端 IP（验证码频限、登录锁定）的场景，统一调用本工具，禁止直接使用 {@code getRemoteAddr()}。
 * </p>
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code CaptchaController}（验证码请求频率限制按客户端 IP）、
 *           {@code LoginController}（登录锁定按客户端 IP）。
 */
public class IpUtils
{
    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_IP = "0.0.0.0";
    private static final String COMMA = ",";

    private IpUtils()
    {
    }

    /**
     * 解析客户端真实 IP。
     *
     * @param request HTTP 请求（可为 {@code null}）
     * @return 客户端 IP 字符串；无法解析时返回 {@link #DEFAULT_IP}
     */
    public static String getClientIp(HttpServletRequest request)
    {
        if (request == null)
        {
            return DEFAULT_IP;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (isBlank(ip))
        {
            ip = request.getHeader("X-Real-IP");
        }
        if (isBlank(ip))
        {
            ip = request.getRemoteAddr();
        }
        if (isBlank(ip))
        {
            return DEFAULT_IP;
        }
        // X-Forwarded-For 可能包含多个代理IP（client, proxy1, proxy2），取第一个
        if (ip.contains(COMMA))
        {
            ip = ip.substring(0, ip.indexOf(COMMA)).trim();
        }
        return ip;
    }

    /** 判断字符串是否为空或值为 "unknown"（大小写不敏感）。 */
    private static boolean isBlank(String s)
    {
        return s == null || s.isBlank() || UNKNOWN.equalsIgnoreCase(s);
    }
}
