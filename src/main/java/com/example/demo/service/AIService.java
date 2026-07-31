package com.example.demo.service;

import com.example.demo.dto.AIResult;
import com.example.demo.model.Message;

import java.util.List;
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

    /** 流式对话：逐块回调 onChunk，结束回调 onComplete，异常回调 onError */
    void streamAnalyze(String systemPrompt, List<Message> history,
                       Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError);

    String getProvider();
    String getPromptVersion();
}
