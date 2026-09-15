package com.example.demo.tool;

import com.example.demo.model.User;

import java.util.Map;

/**
 * 只读 Tool 抽象 —— 定义 + 执行。
 * <p>
 * 约束：Tool 只做只读操作；参数校验与当前用户权限在 execute 内完成
 * （复用底层 Service 的归属校验，不重复造轮子）。
 */
public interface Tool {

    ToolDefinition definition();

    /** @param arguments 模型/调用方传来的参数（key→原始值，可能是 Number/String/List） */
    ToolResult execute(Map<String, Object> arguments, User user);
}
