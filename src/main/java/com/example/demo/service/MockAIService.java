package com.example.demo.service;

import com.example.demo.dto.AIResult;
import com.example.demo.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mock AI 服务 —— 不调用真实 API，返回固定模板结果
 * <p>
 * 当 ai.provider=mock 或未配置 ai.provider 时生效。
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(MockAIService.class);

    @Override
    public AIResult analyze(String inputText, String taskType) {
        log.info("Mock AI 分析开始: taskType={}, inputText={}", taskType, inputText);

        // 模拟耗时（让日志看起来更真实）
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        AIResult result = new AIResult();
        result.setSummary("这是对输入内容的模拟摘要：\"" + inputText + "\" 已完成初步分析。");
        result.setConclusion("模拟分析结论：输入内容属于" + taskType + "范畴，未发现明显异常。");
        result.setSuggestion("后续可将 ai.provider 切换为 openai，接入真实大模型 API。");
        result.setInputTokens(0);
        result.setOutputTokens(0);
        result.setModelName("mock");

        log.info("Mock AI 分析完成: tokensUsed=0, model=mock");
        return result;
    }

    @Override
    public String getProvider() {
        return "mock";
    }

    @Override
    public void streamAnalyze(String systemPrompt, List<Message> history,
                              Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        // 模拟流式输出：逐字推送
        String fullResponse = "这是 Mock 流式回复。根据你的" + history.size() + "条历史消息，" +
                "这是一个模拟的多轮对话响应。实际部署时将返回真实 AI 流式输出。";
        new Thread(() -> {
            try {
                for (char c : fullResponse.toCharArray()) {
                    onChunk.accept(String.valueOf(c));
                    Thread.sleep(30); // 模拟 30ms/字
                }
                onComplete.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onError.accept(e);
            } catch (Exception e) {
                onError.accept(e);
            }
        }).start();
    }

    @Override
    public String getPromptVersion() {
        return "mock-v1";
    }
}
