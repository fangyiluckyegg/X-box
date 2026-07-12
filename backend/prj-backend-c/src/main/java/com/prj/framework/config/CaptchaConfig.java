package com.prj.framework.config;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import static com.google.code.kaptcha.Constants.*;

/**
 * 验证码（Kaptcha）配置。
 *
 * <p>职责：
 * 注册名为 {@code captchaProducerMath} 的 {@link DefaultKaptcha} Bean（数学算式验证码生产者），
 * 并指定自定义的文本生成器 {@link KaptchaTextCreator}（生成"a*b=?@结果"形式的算式文本）。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code KaptchaTextCreator}（自定义文本生成器）。
 * - 被依赖：{@code CaptchaController}（按名称 {@code captchaProducerMath} 注入并使用）。
 */
@Configuration
public class CaptchaConfig
{
    /** 注册数学验证码生产者 Bean，名称为 captchaProducerMath。 */
    @Bean(name = "captchaProducerMath")
    public DefaultKaptcha getKaptchaBeanMath()
    {
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        Properties properties = new Properties();
        properties.setProperty(KAPTCHA_SESSION_CONFIG_KEY, "kaptchaCodeMath");
        // 验证码文本生成器
        properties.setProperty(KAPTCHA_TEXTPRODUCER_IMPL, "com.prj.framework.config.KaptchaTextCreator");
        Config config = new Config(properties);
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }
}
