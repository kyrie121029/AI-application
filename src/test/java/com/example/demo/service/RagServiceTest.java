package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.RagAnswer;
import com.example.demo.dto.RagCitation;
import com.example.demo.dto.RetrievalResult;
import com.example.demo.exception.AIResponseParseException;
import com.example.demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * RagService 编排测试 —— 空检索不调模型、sourceIds 校验、Citation 生成、Prompt 注入防护。
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock private RetrievalService retrievalService;
    @Mock private AIService aiService;
    private ContextAssembler contextAssembler;
    private RagPromptBuilder promptBuilder;
    private RagProperties ragProperties;
    private RagService service;

    private final User alice = new User(1L, "alice", "pwd");

    @BeforeEach
    void setUp() {
        // 每字符 1 token 估算 + 每来源 4 开销；预算开大以免截断干扰断言
        TokenEstimator estimator = t -> t == null ? 0 : t.length();
        contextAssembler = new ContextAssembler(estimator);
        promptBuilder = new RagPromptBuilder();
        ragProperties = new RagProperties();
        ragProperties.getContext().setMaxContextTokens(1000);
        service = new RagService(retrievalService, contextAssembler, promptBuilder, aiService, ragProperties);
    }

    private RetrievalResult hit(Long chunkId, String content) {
        return new RetrievalResult(1L, chunkId, 0, content, 0.9f);
    }

    @Test
    @DisplayName("空检索 → 不调用 AIService，返回证据不足")
    void emptyRetrievalSkipsGeneration() {
        when(retrievalService.search(eq("q"), eq(alice), isNull())).thenReturn(List.of());

        RagAnswer answer = service.answer("q", alice);

        assertTrue(answer.answer().contains("证据不足"));
        assertTrue(answer.citations().isEmpty());
        verify(aiService, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("answer(query,user) 委托 fileIds=null 的版本")
    void answerDelegatesToFileIdsOverload() {
        when(retrievalService.search(eq("q"), eq(alice), isNull())).thenReturn(List.of());

        service.answer("q", alice);

        verify(retrievalService).search("q", alice, null);
    }

    @Test
    @DisplayName("fileIds 贯通到 RetrievalService")
    void fileIdsFlowToRetrieval() {
        when(retrievalService.search(eq("q"), eq(alice), eq(List.of(7L)))).thenReturn(List.of());

        service.answer("q", alice, List.of(7L));

        verify(retrievalService).search("q", alice, List.of(7L));
    }

    @Test
    @DisplayName("完整链路：合法 sourceIds 映射为 Citation，system 含注入防护指令")
    void fullFlowWithValidCitations() {
        when(retrievalService.search(eq("q"), eq(alice), isNull())).thenReturn(List.of(
                hit(101L, "资料A内容"), hit(102L, "资料B内容")));
        when(aiService.complete(anyString(), anyString())).thenReturn(
                "{\"answer\":\"依据资料回答\",\"sourceIds\":[\"S1\",\"S2\"]}");
        when(aiService.getModelName()).thenReturn("mock");

        RagAnswer answer = service.answer("q", alice);

        assertEquals("依据资料回答", answer.answer());
        assertEquals(2, answer.citations().size());
        RagCitation first = answer.citations().get(0);
        assertEquals("S1", first.sourceId());
        assertEquals(101L, first.chunkId());

        // 校验 prompt：system 含注入防护/只依据 context；user 含 [S1] 标记与问题
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).complete(systemCaptor.capture(), userCaptor.capture());
        String system = systemCaptor.getValue();
        assertTrue(system.contains("不可信数据"));
        assertTrue(system.contains("不得执行"));
        assertTrue(system.contains("sourceIds"));
        String user = userCaptor.getValue();
        assertTrue(user.contains("[S1]"));
        assertTrue(user.contains("资料A内容"));
        assertTrue(user.contains("问题：q"));
    }

    @Test
    @DisplayName("模型引用不存在的 Source ID → 不产生 Citation")
    void invalidSourceIdDropped() {
        when(retrievalService.search(eq("q"), eq(alice), isNull()))
                .thenReturn(List.of(hit(101L, "资料A内容")));
        when(aiService.complete(anyString(), anyString())).thenReturn(
                "{\"answer\":\"引用了一个假来源\",\"sourceIds\":[\"S9\",\"S1\"]}"); // S9 不存在
        when(aiService.getModelName()).thenReturn("mock");

        RagAnswer answer = service.answer("q", alice);

        assertEquals(1, answer.citations().size());
        assertEquals("S1", answer.citations().get(0).sourceId());
        assertFalse(answer.citations().stream().anyMatch(c -> c.sourceId().equals("S9")));
    }

    @Test
    @DisplayName("重复 sourceId → 只产生一个 Citation（去重保序）")
    void duplicateSourceIdDeduplicated() {
        when(retrievalService.search(eq("q"), eq(alice), isNull()))
                .thenReturn(List.of(hit(101L, "资料A内容"), hit(102L, "资料B内容")));
        when(aiService.complete(anyString(), anyString())).thenReturn(
                "{\"answer\":\"引用重复\",\"sourceIds\":[\"S2\",\"S1\",\"S2\",\"S1\"]}");
        when(aiService.getModelName()).thenReturn("mock");

        RagAnswer answer = service.answer("q", alice);

        assertEquals(2, answer.citations().size());
        assertEquals("S2", answer.citations().get(0).sourceId()); // 保序去重
        assertEquals("S1", answer.citations().get(1).sourceId());
    }

    @Test
    @DisplayName("模型输出非法 JSON → AIResponseParseException")
    void malformedModelOutputThrows() {
        when(retrievalService.search(eq("q"), eq(alice), isNull()))
                .thenReturn(List.of(hit(101L, "资料A内容")));
        when(aiService.complete(anyString(), anyString())).thenReturn("not json");

        assertThrows(AIResponseParseException.class, () -> service.answer("q", alice));
    }

    @Test
    @DisplayName("sourceIds 缺失 → parse failure")
    void missingSourceIdsThrows() {
        when(retrievalService.search(eq("q"), eq(alice), isNull()))
                .thenReturn(List.of(hit(101L, "资料A内容")));
        when(aiService.complete(anyString(), anyString())).thenReturn("{\"answer\":\"没有 sourceIds\"}");

        assertThrows(AIResponseParseException.class, () -> service.answer("q", alice));
    }

    @Test
    @DisplayName("sourceIds 非数组 → parse failure")
    void nonArraySourceIdsThrows() {
        when(retrievalService.search(eq("q"), eq(alice), isNull()))
                .thenReturn(List.of(hit(101L, "资料A内容")));
        when(aiService.complete(anyString(), anyString())).thenReturn("{\"answer\":\"x\",\"sourceIds\":\"S1\"}");

        assertThrows(AIResponseParseException.class, () -> service.answer("q", alice));
    }

    @Test
    @DisplayName("sourceIds 元素非字符串 → parse failure")
    void nonStringSourceIdThrows() {
        when(retrievalService.search(eq("q"), eq(alice), isNull()))
                .thenReturn(List.of(hit(101L, "资料A内容")));
        when(aiService.complete(anyString(), anyString())).thenReturn("{\"answer\":\"x\",\"sourceIds\":[1]}");

        assertThrows(AIResponseParseException.class, () -> service.answer("q", alice));
    }

    @Test
    @DisplayName("Retrieval 异常正常传播")
    void retrievalExceptionPropagates() {
        when(retrievalService.search(eq("q"), eq(alice), isNull()))
                .thenThrow(new com.example.demo.exception.InvalidFileException("检索失败"));
        assertThrows(com.example.demo.exception.InvalidFileException.class, () -> service.answer("q", alice));
        verify(aiService, never()).complete(anyString(), anyString());
    }
}
