package com.prj.service.embedding;

import com.prj.exception.EmbeddingException;
import com.prj.service.embedding.impl.OllamaEmbeddingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OllamaEmbeddingServiceImpl 单元测试：以 Mockito mock 底层 {@link IOllamaEmbedClient} 缝，
 * 覆盖「正常 + 4 类异常（TIMEOUT/CONNECTION/EMPTY/PARTIAL）」共 6 个用例，无需 mockito-inline。
 */
@ExtendWith(MockitoExtension.class)
class OllamaEmbeddingServiceImplTest
{
    @Mock
    private IOllamaEmbedClient embedClient;

    private OllamaEmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp()
    {
        embeddingService = new OllamaEmbeddingServiceImpl(embedClient);
    }

    private List<double[]> vecs(int n)
    {
        List<double[]> list = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            list.add(new double[]{1.0, 0.0});
        }
        return list;
    }

    private List<String> texts(int n)
    {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            list.add("t" + i);
        }
        return list;
    }

    @Test
    @DisplayName("①正常：整段向量化返回与输入数量一致的映射")
    void normal_returnsAllVectors() throws EmbeddingException
    {
        List<String> input = texts(5);
        when(embedClient.embedBatch(anyList())).thenReturn(vecs(5));

        Map<String, double[]> result = embeddingService.embed(input);

        assertEquals(5, result.size());
        assertTrue(result.containsKey("t0"));
        verify(embedClient, times(1)).embedBatch(anyList());
    }

    @Test
    @DisplayName("②TIMEOUT：底层抛出超时异常，向上抛出 TIMEOUT 分类（保留 cause）")
    void timeout_throwsTimeoutCategory()
    {
        List<String> input = texts(3);
        when(embedClient.embedBatch(anyList()))
                .thenThrow(new EmbeddingException(EmbeddingException.Category.TIMEOUT, "超时", new IOException("timeout")));

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> embeddingService.embed(input));
        assertEquals(EmbeddingException.Category.TIMEOUT, ex.getCategory());
        assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("③CONNECTION：底层抛出连接失败异常，向上抛出 CONNECTION 分类")
    void connection_throwsConnectionCategory()
    {
        List<String> input = texts(3);
        when(embedClient.embedBatch(anyList()))
                .thenThrow(new EmbeddingException(EmbeddingException.Category.CONNECTION, "连接失败", new IOException("refused")));

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> embeddingService.embed(input));
        assertEquals(EmbeddingException.Category.CONNECTION, ex.getCategory());
    }

    @Test
    @DisplayName("④EMPTY：底层抛出空结果异常，向上抛出 EMPTY 分类")
    void empty_throwsEmptyCategory()
    {
        List<String> input = texts(3);
        when(embedClient.embedBatch(anyList()))
                .thenThrow(new EmbeddingException(EmbeddingException.Category.EMPTY, "空结果"));

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> embeddingService.embed(input));
        assertEquals(EmbeddingException.Category.EMPTY, ex.getCategory());
    }

    @Test
    @DisplayName("⑤PARTIAL：部分批次成功、部分失败，抛出 PARTIAL 分类")
    void partial_someBatchesFail_throwsPartialCategory() throws EmbeddingException
    {
        // 150 条 → 分 2 批（100 + 50）。第一批成功，第二批超时。
        List<String> input = texts(150);
        when(embedClient.embedBatch(anyList()))
                .thenReturn(vecs(100))                                                  // 第一批 100 条成功
                .thenThrow(new EmbeddingException(EmbeddingException.Category.TIMEOUT, "超时")); // 第二批失败

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> embeddingService.embed(input));
        assertEquals(EmbeddingException.Category.PARTIAL, ex.getCategory());
        assertTrue(ex.getMessage().contains("部分文本向量化失败"));
    }

    @Test
    @DisplayName("边界：空输入返回空映射且不调用底层")
    void emptyInput_returnsEmptyMap() throws EmbeddingException
    {
        Map<String, double[]> result = embeddingService.embed(new ArrayList<>());
        assertTrue(result.isEmpty());
        verify(embedClient, never()).embedBatch(anyList());
    }

    @Test
    @DisplayName("边界：底层返回数量与输入不符，归类为 EMPTY 并计入失败")
    void mismatchCount_classifiedAsEmpty()
    {
        // 输入 3 条，底层仅返回 2 条 → 数量不符 → EMPTY
        when(embedClient.embedBatch(anyList())).thenReturn(Arrays.asList(new double[]{1, 0}, new double[]{0, 1}));

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> embeddingService.embed(texts(3)));
        assertEquals(EmbeddingException.Category.EMPTY, ex.getCategory());
    }
}
