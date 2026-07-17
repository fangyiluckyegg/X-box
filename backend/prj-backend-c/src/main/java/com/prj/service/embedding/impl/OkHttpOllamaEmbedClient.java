package com.prj.service.embedding.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.prj.exception.EmbeddingException;
import com.prj.service.embedding.IOllamaEmbedClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 OkHttp 调 Ollama {@code /api/embed} 的真实向量化客户端。
 *
 * <p>实现 {@link IOllamaEmbedClient} 缝，由 Spring 以单例 bean 注入；
 * {@link OkHttpClient} 由 {@code @Bean}（见 {@code AsyncConfig}）提供，连接池 20 连接复用，
 * 连接 30s / 读取 120s。异常按类型分类为 {@link EmbeddingException.Category}：
 * SocketTimeout → TIMEOUT；其它 IOException → CONNECTION；
 * HTTP 非成功或 embeddings 为空/数量不符 → EMPTY。
 */
@Component
public class OkHttpOllamaEmbedClient implements IOllamaEmbedClient
{
    private static final Logger logger = LoggerFactory.getLogger(OkHttpOllamaEmbedClient.class);
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String embedUrl;
    private final String model;

    public OkHttpOllamaEmbedClient(OkHttpClient httpClient,
                                   @Value("${AI_SERVICE_URL:http://dev-prj-llama:11434}") String aiServiceBaseUrl,
                                   @Value("${AI_EMBED_MODEL:bge-m3:latest}") String embedModel)
    {
        this.httpClient = httpClient;
        String base = (aiServiceBaseUrl == null) ? "" : aiServiceBaseUrl.trim();
        if (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        this.embedUrl = base + "/api/embed";
        this.model = embedModel;
    }

    @Override
    public List<double[]> embedBatch(List<String> texts)
    {
        JSONObject req = new JSONObject();
        req.put("model", model);
        req.put("input", texts);
        RequestBody body = RequestBody.create(req.toString(), JSON_MEDIA);
        Request request = new Request.Builder()
                .url(embedUrl)
                .post(body)
                .build();
        try (Response resp = httpClient.newCall(request).execute())
        {
            int code = resp.code();
            String rawBody = (resp.body() != null) ? resp.body().string() : "";
            if (!resp.isSuccessful())
            {
                throw new EmbeddingException(EmbeddingException.Category.EMPTY,
                        "Ollama 异常，状态码：" + code + "，响应：" + truncate(rawBody));
            }
            JSONObject json = JSON.parseObject(rawBody);
            // /api/embed 的响应字段为 embeddings（二维数组，与 input 顺序一致）
            JSONArray embeds = json.getJSONArray("embeddings");
            if (embeds == null || embeds.isEmpty())
            {
                throw new EmbeddingException(EmbeddingException.Category.EMPTY, "Ollama 未返回向量(embeddings 为空)");
            }
            if (embeds.size() != texts.size())
            {
                throw new EmbeddingException(EmbeddingException.Category.EMPTY,
                        "Ollama 返回向量数量(" + embeds.size() + ")与输入数量(" + texts.size() + ")不一致");
            }
            List<double[]> result = new ArrayList<>(embeds.size());
            for (int k = 0; k < embeds.size(); k++)
            {
                JSONArray e = embeds.getJSONArray(k);
                if (e == null || e.isEmpty())
                {
                    throw new EmbeddingException(EmbeddingException.Category.EMPTY, "Ollama 返回某条向量为空");
                }
                double[] arr = new double[e.size()];
                for (int i = 0; i < arr.length; i++)
                {
                    arr[i] = e.getDoubleValue(i);
                }
                result.add(arr);
            }
            return result;
        }
        catch (EmbeddingException e)
        {
            throw e;
        }
        catch (SocketTimeoutException e)
        {
            throw new EmbeddingException(EmbeddingException.Category.TIMEOUT, "Ollama 向量化超时", e);
        }
        catch (IOException e)
        {
            throw new EmbeddingException(EmbeddingException.Category.CONNECTION, "Ollama 向量化连接失败", e);
        }
    }

    private static String truncate(String s)
    {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
