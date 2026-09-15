package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.*;
import com.example.demo.exception.AIResponseParseException;
import com.example.demo.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAG 编排 —— Retrieval → Context Assembly → Prompt → AIService → Citation 校验 → RagAnswer。
 * <p>
 * 不新建模型 HTTP Client（复用 AIService）。空检索/空 Context：不调用 AIService，返回"证据不足"。
 * Citation 单一通道：模型只通过 sourceIds 引用；后端仅接受本次 Context 中合法 Source，去重保序。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String INSUFFICIENT = "证据不足：检索到的资料不足以回答该问题。";

    private final RetrievalService retrievalService;
    private final ContextAssembler contextAssembler;
    private final RagPromptBuilder promptBuilder;
    private final AIService aiService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagService(RetrievalService retrievalService,
                      ContextAssembler contextAssembler,
                      RagPromptBuilder promptBuilder,
                      AIService aiService,
                      RagProperties ragProperties) {
        this.retrievalService = retrievalService;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.aiService = aiService;
        this.ragProperties = ragProperties;
    }

    /** RAG 问答（当前用户全部可检索文件），委托 fileIds 版本 */
    public RagAnswer answer(String query, User user) {
        return answer(query, user, null);
    }

    /** RAG 问答（限定 fileIds 可空） */
    public RagAnswer answer(String query, User user, List<Long> fileIds) {
        List<RetrievalResult> hits = retrievalService.search(query, user, fileIds);

        ContextAssemblyResult assembly = contextAssembler.assemble(hits,
                ragProperties.getContext().getMaxContextTokens());

        if (assembly.sources().isEmpty()) {
            log.info("RAG 证据不足，跳过生成: hits={}", hits.size());
            return new RagAnswer(INSUFFICIENT, List.of(), null);
        }

        String system = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(query, assembly);
        String raw = aiService.complete(system, userPrompt);

        RagModelOutput model = parseStrict(raw);
        return new RagAnswer(model.answer(), toCitations(model.sourceIds(), assembly),
                aiService.getModelName());
    }

    /** sourceIds → 仅保留本次 Context 中真实存在的来源，去重且保持顺序 */
    private List<RagCitation> toCitations(List<String> sourceIds, ContextAssemblyResult assembly) {
        Map<String, RagContextSource> bySource = new LinkedHashMap<>();
        for (RagContextSource s : assembly.sources()) bySource.put(s.sourceId(), s);

        Set<String> seen = new LinkedHashSet<>();
        List<RagCitation> citations = new ArrayList<>();
        for (String id : sourceIds) {
            if (!seen.add(id)) continue; // 去重
            RagContextSource source = bySource.get(id);
            if (source == null) {
                log.warn("模型引用了非法 Source ID，丢弃: {}", id);
                continue;
            }
            citations.add(new RagCitation(source.sourceId(), source.fileId(),
                    source.chunkId(), source.chunkIndex(), source.score()));
        }
        return citations;
    }

    /**
     * 严格解析模型结构化输出：
     *   root 必须为 object；answer 为非空 string；sourceIds 必须存在且为 array，元素均为 string。
     * 违反 → AIResponseParseException。
     */
    private RagModelOutput parseStrict(String raw) {
        JsonNode node;
        try {
            node = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new AIResponseParseException("RAG 模型输出不是合法 JSON", raw);
        }
        if (node == null || !node.isObject()) {
            throw new AIResponseParseException("RAG 模型输出必须是 JSON 对象", raw);
        }
        JsonNode answerNode = node.get("answer");
        if (answerNode == null || !answerNode.isTextual() || answerNode.asText().isBlank()) {
            throw new AIResponseParseException("RAG 模型输出缺少非空 answer", raw);
        }
        JsonNode idsNode = node.get("sourceIds");
        if (idsNode == null || !idsNode.isArray()) {
            throw new AIResponseParseException("RAG 模型输出 sourceIds 缺失或非数组", raw);
        }
        List<String> sourceIds = new ArrayList<>();
        for (JsonNode id : idsNode) {
            if (!id.isTextual()) {
                throw new AIResponseParseException("RAG 模型输出 sourceIds 元素非字符串", raw);
            }
            sourceIds.add(id.asText());
        }
        return new RagModelOutput(answerNode.asText(), sourceIds);
    }

    /** 模型结构化输出内部载体 */
    private record RagModelOutput(String answer, List<String> sourceIds) {
    }
}
