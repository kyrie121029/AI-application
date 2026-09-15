package com.example.demo.dto;

import java.util.Map;

/**
 * Agent 单步执行轨迹。
 */
public record AgentTrace(
        int round,
        String toolName,
        Map<String, Object> arguments,
        long latencyMs,
        boolean success,
        String errorCode,
        String error
) {
}
