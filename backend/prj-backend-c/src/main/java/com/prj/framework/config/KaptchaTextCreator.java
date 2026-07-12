package com.prj.framework.config;

import com.google.code.kaptcha.text.impl.DefaultTextCreator;

import java.util.Random;

/**
 * 自定义验证码文本生成器（数学算式）。
 *
 * <p>职责：
 * 继承 Kaptcha 的 {@link DefaultTextCreator}，生成形如 {@code "3*7=?@21"} 的验证码文本——
 * 其中 "@" 之前为展示给用户看的算式，"@" 之后为正确答案。
 * 该格式与 {@code CaptchaController} 中 {@code substring(lastIndexOf("@"))} 的解析方式严格对应。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code CaptchaConfig}（配置为 captchaProducerMath 的文本生成器）。
 * - 配合：{@code CaptchaController}（生成时拆分算式与答案，答案存入 Redis）。
 */
public class KaptchaTextCreator extends DefaultTextCreator
{
    private static final String[] CNUMBERS = "0,1,2,3,4,5,6,7,8,9,10".split(",");

    /** 生成一条随机乘法算式验证码文本（格式：数字*数字=?@结果）。 */
    @Override
    public String getText()
    {
        Integer result = 0;
        Random random = new Random();
        int x = random.nextInt(10);
        int y = random.nextInt(10);
        StringBuilder strBuilder = new StringBuilder();
        result = x * y;
        strBuilder.append(CNUMBERS[x]);
        strBuilder.append("*");
        strBuilder.append(CNUMBERS[y]);
        strBuilder.append("=?@" + result);
        return strBuilder.toString();
    }
}
