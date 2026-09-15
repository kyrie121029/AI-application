package com.example.demo.enums;

/**
 * Agent 结束状态。
 */
public enum AgentStatus {
    SUCCESS,          // 模型直接给出最终答案
    MAX_ROUNDS,       // 达到最大工具轮数安全终止
    DUPLICATE_CALL,   // 检测到重复 Tool + 相同参数，安全终止
    FORBIDDEN,        // 工具返回越权，停止
    TIMEOUT,          // 超过总超时
    NO_ANSWER         // 无工具调用也无内容
}
