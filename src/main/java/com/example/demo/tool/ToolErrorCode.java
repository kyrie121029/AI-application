package com.example.demo.tool;

/**
 * Tool 结构化错误码。
 */
public enum ToolErrorCode {
    INVALID_ARGUMENT,   // 参数非法/缺失
    NOT_FOUND,          // 资源不存在
    FORBIDDEN,          // 越权
    TIMEOUT,            // 工具执行超时
    EXECUTION_FAILED,   // 其它执行失败（含未知工具）
    UNKNOWN
}
