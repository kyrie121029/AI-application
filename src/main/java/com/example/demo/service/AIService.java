package com.example.demo.service;

import com.example.demo.dto.AIResult;
import com.example.demo.dto.ChatMessage;
import com.example.demo.model.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * AI 服务接口
 * <p>
 * analyze():      一次性调用（Phase 4 的任务分析）
 * streamAnalyze(): 流式调用（Phase 5 的多轮对话）
 */
public interface AIService {

    /** 一次性分析 */
    AIResult analyze(String inputText, String taskType);

    /** 单轮补全（RAG 生成等通用文本场景）：返回模型原始文本，不解析结构 */
    String complete(String systemPrompt, String userPrompt);

    /**
     * OpenAI-compatible chat（可带 tools）。
     *
     * @param messages    类型化消息（system/user/assistant/tool）
     * @param toolSchemas OpenAI tools JSON Schema（可为空）
     * @return 助手消息（content 或 toolCalls）
     */
    ChatMessage chatCompletion(List<ChatMessage> messages, List<Map<String, Object>> toolSchemas);

    /**
     * 流式对话。
     *
     * @param systemPrompt System Prompt
     * @param history      多轮历史消息（不含 System Prompt）
     * @param isCancelled  客户端断开/超时后由调用方置为 true，实现类应尽快停止发送并结束
     * @param onChunk       每收到一个文本片段回调
     * @param onComplete    流正常结束时回调（仅一次）
     * @param onError       流异常时回调（仅一次）
     * @param retryCountOut 输出参数：实现类在真正回调 onComplete/onError 前写入实际重试次数，
     *                      供调用方记入 AIUsageLog（同步/成功路径可写 0）
     */
    void streamAnalyze(String systemPrompt, List<Message> history,
                       BooleanSupplier isCancelled,
                       Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError,
                       AtomicInteger retryCountOut);

    /** 返回当前 AI 提供商标识（用于日志记录） */
    String getProvider();

    /** 返回当前使用的 Prompt 版本号 */
    String getPromptVersion();

    /** 返回当前使用的模型名称（用于日志记录） */
    String getModelName();
}
