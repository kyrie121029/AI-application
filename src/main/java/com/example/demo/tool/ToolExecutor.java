package com.example.demo.tool;

import com.example.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool 执行器 —— 按 toolName + arguments 分发并统一返回 ToolResult。
 * <p>
 * 统一校验：未知 Tool、缺少必需参数 → 直接 error ToolResult；
 * Tool 内部业务/权限异常也收敛为 ToolResult（本阶段不抛到外层，为后续 Agent 循环服务）。
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    public ToolResult execute(String toolName, Map<String, Object> arguments, User user) {
        Tool tool = registry.byName(toolName);
        if (tool == null) {
            return ToolResult.error(toolName, ToolErrorCode.EXECUTION_FAILED,
                    "未知工具: " + toolName, false);
        }
        ToolDefinition def = tool.definition();
        for (String required : def.requiredParams()) {
            Object v = arguments == null ? null : arguments.get(required);
            if (v == null || (v instanceof String s && s.isBlank())) {
                return ToolResult.error(toolName, ToolErrorCode.INVALID_ARGUMENT,
                        "缺少必需参数: " + required, false);
            }
        }
        try {
            return tool.execute(arguments == null ? Map.of() : arguments, user);
        } catch (Exception e) {
            log.warn("Tool 执行异常: tool={}, user={}", toolName, user.getId(), e);
            return ToolResult.error(toolName, ToolErrorCode.EXECUTION_FAILED,
                    "工具执行失败: " + e.getMessage(), false);
        }
    }
}
