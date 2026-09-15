package com.example.demo.service;

import com.example.demo.config.AgentProperties;
import com.example.demo.dto.AgentResult;
import com.example.demo.dto.AgentTrace;
import com.example.demo.dto.ChatMessage;
import com.example.demo.dto.ToolCall;
import com.example.demo.enums.AgentStatus;
import com.example.demo.model.User;
import com.example.demo.tool.ToolDefinition;
import com.example.demo.tool.ToolErrorCode;
import com.example.demo.tool.ToolExecutor;
import com.example.demo.tool.ToolRegistry;
import com.example.demo.tool.ToolResult;
import com.example.demo.tool.ToolSchemaExporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 受控 Agent Loop：
 *   LLM(with tools) → Tool → LLM → ... → Final Answer
 * 执行边界：maxRounds、总 timeout、重复 Tool+参数检测、安全终止状态。
 * Tool 错误结构化（INVALID_ARGUMENT / NOT_FOUND / FORBIDDEN / TIMEOUT / EXECUTION_FAILED）。
 * 记录 Execution Trace（round/tool/args/latency/result/error）。
 * 仅允许只读 Tool（写操作不在此阶段）。
 */
@Service
public class FunctionCallingService {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingService.class);

    private final AIService aiService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FunctionCallingService(AIService aiService, ToolRegistry toolRegistry,
                                  ToolExecutor toolExecutor, AgentProperties agentProperties) {
        this.aiService = aiService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.agentProperties = agentProperties;
    }

    /** 执行受控多轮 Agent Loop */
    public AgentResult run(String systemPrompt, String userText, User user) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userText));

        List<Map<String, Object>> toolSchemas =
                ToolSchemaExporter.toOpenaiTools(new ArrayList<>(toolRegistry.allDefinitions().values()));

        List<AgentTrace> trace = new ArrayList<>();
        Set<String> executedCalls = new HashSet<>();
        long deadline = System.nanoTime() + agentProperties.getTimeout().toNanos();
        int round = 0;

        while (true) {
            if (System.nanoTime() > deadline) {
                return new AgentResult(AgentStatus.TIMEOUT, "Agent 执行超时", round, trace);
            }
            if (round >= agentProperties.getMaxRounds()) {
                return new AgentResult(AgentStatus.MAX_ROUNDS, "达到最大工具轮数，安全终止", round, trace);
            }

            ChatMessage assistant = aiService.chatCompletion(messages, toolSchemas);
            List<ToolCall> calls = assistant.toolCalls();
            String content = assistant.content();

            if (calls == null || calls.isEmpty()) {
                String answer = content == null || content.isBlank() ? "（无内容）" : content;
                return new AgentResult(AgentStatus.SUCCESS, answer, round, trace);
            }

            // 本轮要执行工具：round 计数 +1
            round++;
            boolean stoppedForbidden = false;
            boolean duplicate = false;

            // 记录 assistant tool_calls 到 messages
            messages.add(ChatMessage.assistant(content, calls));

            for (ToolCall call : calls) {
                String key = call.name() + ":" + canonicalArgs(call.arguments());
                if (!executedCalls.add(key)) {
                    duplicate = true;
                    log.warn("检测到重复 Tool 调用，终止: {}", key);
                    break;
                }

                long start = System.nanoTime();
                ToolResult result = toolExecutor.execute(call.name(), call.arguments(), user);
                long latencyMs = (System.nanoTime() - start) / 1_000_000;

                trace.add(new AgentTrace(round, call.name(), call.arguments(), latencyMs,
                        result.success(),
                        result.errorCode() == null ? null : result.errorCode().name(),
                        result.error()));

                messages.add(ChatMessage.tool(call.id(), toJson(result)));

                if (result.errorCode() == ToolErrorCode.FORBIDDEN) {
                    stoppedForbidden = true;
                    break;
                }
            }

            if (duplicate) {
                return new AgentResult(AgentStatus.DUPLICATE_CALL, "检测到重复工具调用，安全终止", round, trace);
            }
            if (stoppedForbidden) {
                return new AgentResult(AgentStatus.FORBIDDEN, "工具返回越权，停止执行", round, trace);
            }
            // 否则进入下一轮 LLM（参数错误 / 资源不存在等以 error 回填，模型可重新决策）
        }
    }

    private String canonicalArgs(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(
                    arguments == null ? Map.of() : new TreeMap<>(arguments));
        } catch (Exception e) {
            return String.valueOf(arguments);
        }
    }

    private String toJson(ToolResult result) {
        try {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("toolName", result.toolName());
            map.put("success", result.success());
            map.put("data", result.data());
            map.put("errorCode", result.errorCode() == null ? null : result.errorCode().name());
            map.put("error", result.error());
            map.put("retryable", result.retryable());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"结果序列化失败\"}";
        }
    }
}
