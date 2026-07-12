package com.prj.controller;

import com.google.code.kaptcha.Producer;
import com.prj.common.constant.Constants;
import com.prj.common.core.domain.AjaxResult;
import com.prj.common.core.redis.RedisCache;
import com.prj.common.utils.IpUtils;
import com.prj.common.utils.sign.Base64;
import com.prj.common.utils.uuid.IdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 验证码控制器（Captcha）。
 *
 * <p>职责：
 * 对外提供图形验证码生成接口 {@code GET /captchaImage}，随机生成验证码文本与图片，
 * 将验证码答案写入 Redis（以 uuid 为 key），并把图片以 Base64 编码回传给前端。
 * 同时基于客户端 IP 做 5 秒内的请求频率限制，防止验证码被暴力刷取。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code com.google.code.kaptcha.Producer}（由 CaptchaConfig 注入，math 类型验证码）、
 *         {@code RedisCache}（缓存验证码与频率限制标记）、
 *         {@code IpUtils}（解析真实客户端 IP）、
 *         {@code Constants}（验证码缓存 key 前缀与过期时间常量）、
 *         {@code IdUtils}（生成 uuid）、{@code Base64}（图片流编码）。
 * - 被依赖：前端登录页调用该接口获取验证码图片与 uuid。
 *
 * <p>安全说明：验证码答案仅存于服务端 Redis，前端只持有 uuid 与图片，校验在登录时由 LoginService 完成。
 */
@RestController
public class CaptchaController
{
    /** 数学类型验证码生成器，由 Spring 按名称注入（对应 CaptchaConfig 中名为 captchaProducerMath 的 Bean）。 */
    @Resource(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    /** Redis 缓存操作对象，用于存放验证码与频率限制标记。 */
    @Autowired
    private RedisCache redisCache;

    /** 生成验证码     */
    /**
     * 生成并返回图形验证码。
     *
     * @param request  HTTP 请求（用于解析客户端真实 IP 与请求头）
     * @param response HTTP 响应（此处主要用于声明可能抛出的 IO 异常）
     * @return 成功时返回包含 {@code uuid}（验证码标识，前端登录需回传）与 {@code img}（Base64 编码的验证码图片）的 AjaxResult；
     *         若 IP 触发频率限制或图片写出异常，则返回错误结果
     * @throws IOException 验证码图片编码写出失败时抛出，由框架统一处理
     */
    @GetMapping("/captchaImage")
    public AjaxResult getCode(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        // [P2-16-FIX] 基于客户端IP的验证码频率限制，5秒内重复请求拦截，防暴力刷取
        // [C8/C12] 统一通过 IpUtils 解析真实客户端 IP（优先 X-Forwarded-For / X-Real-IP）
        String clientIp = IpUtils.getClientIp(request);
        String rateKey = "captcha:rate:" + clientIp;
        // 若 5 秒频率限制窗口内已请求过，则直接拦截
        if (redisCache.getCacheObject(rateKey) != null) {
            return AjaxResult.error("验证码请求过于频繁，请5秒后再试");
        }
        AjaxResult ajax = AjaxResult.success();

        // 保存验证码信息
        String uuid = IdUtils.simpleUUID();
        String verifyKey = Constants.CAPTCHA_CODE_KEY + uuid;

        String capStr = null, code = null;
        BufferedImage image = null;

        // 生成形如 "1+2@3" 的文本：@ 之前为展示算式，之后为计算答案
        String capText = captchaProducerMath.createText();
        capStr = capText.substring(0, capText.lastIndexOf("@"));
        code = capText.substring(capText.lastIndexOf("@") + 1);
        image = captchaProducerMath.createImage(capStr);

        // 将验证码答案写入 Redis，设置过期时间（分钟）
        redisCache.setCacheObject(verifyKey, code, Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);
        // [P2-16-FIX] 设置5秒频率限制窗口
        redisCache.setCacheObject(rateKey, "1", 5, TimeUnit.SECONDS);
        // 转换流信息写出
        FastByteArrayOutputStream os = new FastByteArrayOutputStream();
        try
        {
            ImageIO.write(image, "jpg", os);
        }
        catch (IOException e)
        {
            return AjaxResult.error(e.getMessage());
        }

        // 将 uuid 与 Base64 编码后的图片返回前端
        ajax.put("uuid", uuid);
        ajax.put("img", Base64.encode(os.toByteArray()));
        return ajax;
    }
}
