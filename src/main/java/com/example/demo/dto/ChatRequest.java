package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 对话请求 —— 用户发送一条消息
 */
public class ChatRequest {

    /** 消息内容最大长度（常量，避免魔法数字） */
    public static final int MAX_CONTENT_LENGTH = 10000;

    /** 对话标题最大长度 */
    public static final int MAX_TITLE_LENGTH = 100;

    /** 可选：新建对话时的标题（第一条消息时生效） */
    @Size(max = MAX_TITLE_LENGTH, message = "对话标题不能超过" + MAX_TITLE_LENGTH + "个字符")
    private String title;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = MAX_CONTENT_LENGTH, message = "消息内容不能超过" + MAX_CONTENT_LENGTH + "个字符")
    private String content;

    /** 可选：幂等请求 ID —— 同一 userId + conversationId + requestId 只执行一次 */
    @Size(max = 64, message = "requestId 不能超过64个字符")
    private String requestId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
