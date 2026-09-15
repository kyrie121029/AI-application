package com.example.demo.dto;

import com.example.demo.enums.AgentStatus;

import java.util.List;

/**
 * Agent 执行结果。
 */
public record AgentResult(
        AgentStatus status,
        String answer,
        int roundsUsed,
        List<AgentTrace> trace
) {
}
