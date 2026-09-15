package com.example.demo.dto;

import java.util.Map;

/**
 * 模型请求的工具调用。
 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {
}
