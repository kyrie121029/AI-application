package com.example.demo.config;

import java.util.Map;

/**
 * Prompt 模板 —— 支持 {变量名} 占位替换
 * <p>
 * 示例：
 *   template = "分析以下{taskType}文本：{inputText}"
 *   render(taskType="情感分析", inputText="服务很好")
 *   → "分析以下情感分析文本：服务很好"
 */
public class PromptTemplate {

    private final String version;
    private final String template;

    public PromptTemplate(String version, String template) {
        this.version = version;
        this.template = template;
    }

    /** 替换模板中的 {key} 为对应 value */
    public String render(Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** 模板版本号，如 task-analysis-v1 */
    public String getVersion() {
        return version;
    }

    /** 原始模板（未替换变量） */
    public String getTemplate() {
        return template;
    }
}
