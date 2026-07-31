package com.example.demo.dto;

import com.example.demo.model.Conversation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 对话响应 —— 对前端的安全视图，不暴露 User 对象内部细节
 */
@Schema(description = "对话响应")
public class ConversationResponse {

    @Schema(description = "对话ID") private Long id;
    @Schema(description = "对话标题") private String title;
    @Schema(description = "创建时间") private LocalDateTime createdAt;

    private ConversationResponse() {}

    public static ConversationResponse from(Conversation c) {
        ConversationResponse r = new ConversationResponse();
        r.id = c.getId();
        r.title = c.getTitle();
        r.createdAt = c.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
