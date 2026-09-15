package com.example.demo.dto;

import java.util.List;

/**
 * 类型化聊天消息 —— business 层不再依赖原始 Map（wire 转换由 AIService 负责）。
 * <p>
 * role 取值：system / user / assistant / tool。
 * assistant 消息可携带 toolCalls（content 可为 null）；tool 消息携带 toolCallId + content。
 */
public record ChatMessage(
        String role,
        String content,
        String toolCallId,
        List<ToolCall> toolCalls
) {
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, null, toolCalls);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }
}
