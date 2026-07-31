package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 对话请求 —— 用户发送一条消息
 */
public class ChatRequest {

    /** 可选：新建对话时的标题（第一条消息时生效） */
    private String title;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
