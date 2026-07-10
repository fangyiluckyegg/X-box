package com.prj.framework.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.Charset;

/**
 * Redis 序列化配置。
 * <p>
 * [C9] 覆写默认 {@code RedisTemplate}：key 统一使用 {@link StringRedisSerializer}，
 * value（含 {@code LoginUser} 等对象）使用 fastjson2 JSON 序列化。
 * </p>
 * <p>
 * 说明：本应使用 {@code com.alibaba.fastjson2.support.spring.data.redis.GenericFastJsonRedisSerializer}，
 * 但该实现位于 {@code fastjson2-extension} / {@code fastjson2-extension-spring6} 模块，
 * 不属于核心 {@code fastjson2} 包（当前 pom 仅引入核心包）。为遵守"零新增第三方依赖"硬约束，
 * 这里用核心包 {@code JSON} API 复刻其等价行为：{@code WriteClassName} 写入 {@code @type}，
 * {@code SupportAutoType} 还原真实类型，因此 {@code LoginUser} 可正确往返。
 * 注意：存入 Redis 的实体敏感字段必须标记不序列化（如 {@code User.password} 已加 {@code @JSONField(serialize=false)}），
 * 杜绝 bcrypt 哈希落盘。
 * </p>
 */
@Configuration
public class RedisConfig
{
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory)
    {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // [C9] JSON 序列化（核心 fastjson2，零新增依赖；等价于 GenericFastJsonRedisSerializer）
        FastJson2RedisSerializer fastJsonSerializer = new FastJson2RedisSerializer();

        // key / hashKey 统一 String 序列化
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // value / hashValue 使用 fastjson2 JSON 序列化
        template.setValueSerializer(fastJsonSerializer);
        template.setHashValueSerializer(fastJsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * [C9] 基于核心 fastjson2 {@code JSON} API 的 Redis 序列化器。
     * <p>序列化写入类型信息（{@code @type}），反序列化按类型还原，保证多态/对象往返正确。</p>
     */
    static class FastJson2RedisSerializer implements RedisSerializer<Object>
    {
        private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

        @Override
        public byte[] serialize(Object object) throws SerializationException
        {
            if (object == null)
            {
                return new byte[0];
            }
            try
            {
                return JSON.toJSONBytes(object, JSONWriter.Feature.WriteClassName);
            }
            catch (Exception ex)
            {
                throw new SerializationException("FastJson2 serialize failed: " + ex.getMessage(), ex);
            }
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException
        {
            if (bytes == null || bytes.length == 0)
            {
                return null;
            }
            try
            {
                return JSON.parseObject(new String(bytes, DEFAULT_CHARSET), Object.class,
                        JSONReader.Feature.SupportAutoType);
            }
            catch (Exception ex)
            {
                throw new SerializationException("FastJson2 deserialize failed: " + ex.getMessage(), ex);
            }
        }
    }
}
