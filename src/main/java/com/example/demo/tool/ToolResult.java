package com.example.demo.tool;

/**
 * Tool 执行结果 —— 成功/失败统一载体（含结构化错误码，供 Agent 决定是否继续）。
 */
public record ToolResult(
        String toolName,
        boolean success,
        Object data,
        ToolErrorCode errorCode,
        String error,
        boolean retryable
) {
    public static ToolResult ok(String toolName, Object data) {
        return new ToolResult(toolName, true, data, null, null, false);
    }

    public static ToolResult error(String toolName, ToolErrorCode code, String error, boolean retryable) {
        return new ToolResult(toolName, false, null, code, error, retryable);
    }
}
