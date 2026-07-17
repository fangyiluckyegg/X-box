package com.prj.service.compare;

import com.prj.exception.EmbeddingException;
import com.prj.service.compare.impl.CompareServiceImpl;
import com.prj.service.embedding.IEmbeddingService;
import com.prj.service.similarity.ISimilarityService;
import com.prj.store.IProgressStore;
import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompareServiceImpl 编排单元测试：mock 全部依赖（embedding/similarity/store/excelReader），
 * 验证正常流程写结果 + 各失败分支写入 failed 进度。
 * 直接构造实例（@Async 在无代理实例上退化为同步执行，便于断言）。
 */
@ExtendWith(MockitoExtension.class)
class CompareServiceImplTest
{
    @Mock
    private IEmbeddingService embeddingService;
    @Mock
    private ISimilarityService similarityService;
    @Mock
    private IProgressStore progressStore;
    @Mock
    private ExcelReader excelReader;

    private CompareServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new CompareServiceImpl(embeddingService, similarityService, progressStore, excelReader);
    }

    private byte[] file()
    {
        return "data".getBytes();
    }

    @Test
    @DisplayName("正常流程：编排完成后写结果且最终进度为 done/100%")
    void happyPath_writesResultAndDone() throws IOException
    {
        List<String> origin = Arrays.asList("a", "b");
        List<String> neu = Arrays.asList("a", "b");
        when(excelReader.readFirstColumnNames(any())).thenReturn(origin, neu);

        Map<String, double[]> vec = new HashMap<>();
        vec.put("a", new double[]{1, 0});
        vec.put("b", new double[]{0, 1});
        when(embeddingService.embed(anyList())).thenReturn(vec, vec);

        List<CompareResultRow> rows = Arrays.asList(new CompareResultRow("a", "a", "a", "a", 1.0, "完全匹配"));
        when(similarityService.compare(any(), any(), any(), any(), any())).thenReturn(rows);

        service.performCompare("alice", file(), file());

        verify(progressStore).saveResult(eq("alice"), eq(rows));
        ArgumentCaptor<ProgressVo> captor = ArgumentCaptor.forClass(ProgressVo.class);
        verify(progressStore, atLeastOnce()).saveProgress(eq("alice"), captor.capture());
        ProgressVo last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("done", last.getStage());
        assertEquals(100, last.getPercent());
        verify(progressStore, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("失败分支：原始文件无有效数据 → markFailed(原始文件无有效数据)")
    void originEmpty_marksFailed() throws IOException
    {
        when(excelReader.readFirstColumnNames(any())).thenReturn(Collections.emptyList(), Collections.emptyList());

        service.performCompare("alice", file(), file());

        verify(progressStore).markFailed(eq("alice"), eq("原始文件无有效数据"));
        verify(progressStore, never()).saveResult(any(), any());
    }

    @Test
    @DisplayName("失败分支：新数据向量化抛 TIMEOUT → markFailed(Ollama 超时，请检查向量服务)")
    void newEmbedTimeout_marksFailedWithTimeoutMessage() throws IOException
    {
        List<String> origin = Arrays.asList("a");
        List<String> neu = Arrays.asList("b");
        when(excelReader.readFirstColumnNames(any())).thenReturn(origin, neu);
        Map<String, double[]> vec = new HashMap<>();
        vec.put("a", new double[]{1, 0});
        when(embeddingService.embed(anyList())).thenReturn(vec)
                .thenThrow(new EmbeddingException(EmbeddingException.Category.TIMEOUT, "超时"));

        service.performCompare("alice", file(), file());

        verify(progressStore).markFailed(eq("alice"), contains("Ollama 超时，请检查向量服务"));
        verify(progressStore, never()).saveResult(any(), any());
    }

    @Test
    @DisplayName("失败分支：新数据向量化覆盖率不达标 → markFailed(新数据向量化失败...)")
    void newCoverageBelowThreshold_marksFailed() throws IOException
    {
        List<String> origin = Arrays.asList("a");
        List<String> neu = Arrays.asList("b", "c");
        when(excelReader.readFirstColumnNames(any())).thenReturn(origin, neu);
        Map<String, double[]> ovm = new HashMap<>();
        ovm.put("a", new double[]{1, 0});
        when(embeddingService.embed(anyList())).thenReturn(ovm, Collections.emptyMap());

        service.performCompare("alice", file(), file());

        verify(progressStore).markFailed(eq("alice"), contains("新数据向量化失败"));
        verify(progressStore, never()).saveResult(any(), any());
    }

    @Test
    @DisplayName("失败分支：读取 Excel 抛异常 → markFailed(比对失败：...)")
    void excelReadThrows_marksFailedGeneric() throws IOException
    {
        when(excelReader.readFirstColumnNames(any())).thenThrow(new RuntimeException("IO boom"));

        service.performCompare("alice", file(), file());

        verify(progressStore).markFailed(eq("alice"), contains("比对失败："));
        verify(progressStore, never()).saveResult(any(), any());
    }
}
