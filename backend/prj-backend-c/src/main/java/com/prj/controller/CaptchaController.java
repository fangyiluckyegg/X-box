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

@RestController
public class CaptchaController
{
    @Resource(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    @Autowired
    private RedisCache redisCache;

    /** 生成验证码     */
    @GetMapping("/captchaImage")
    public AjaxResult getCode(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        // [P2-16-FIX] 基于客户端IP的验证码频率限制，5秒内重复请求拦截，防暴力刷取
        // [C8/C12] 统一通过 IpUtils 解析真实客户端 IP（优先 X-Forwarded-For / X-Real-IP）
        String clientIp = IpUtils.getClientIp(request);
        String rateKey = "captcha:rate:" + clientIp;
        if (redisCache.getCacheObject(rateKey) != null) {
            return AjaxResult.error("验证码请求过于频繁，请5秒后再试");
        }
        AjaxResult ajax = AjaxResult.success();

        // 保存验证码信息
        String uuid = IdUtils.simpleUUID();
        String verifyKey = Constants.CAPTCHA_CODE_KEY + uuid;

        String capStr = null, code = null;
        BufferedImage image = null;

        String capText = captchaProducerMath.createText();
        capStr = capText.substring(0, capText.lastIndexOf("@"));
        code = capText.substring(capText.lastIndexOf("@") + 1);
        image = captchaProducerMath.createImage(capStr);

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

        ajax.put("uuid", uuid);
        ajax.put("img", Base64.encode(os.toByteArray()));
        return ajax;
    }
}
