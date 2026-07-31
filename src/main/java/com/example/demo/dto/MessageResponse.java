package com.example.demo.dto;

import com.example.demo.model.Message;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 消息响应 —— 对前端的安全视图
 */
@Schema(description = "消息响应")
public class MessageResponse {

    @Schema(description = "消息ID") private Long id;
    @Schema(description = "角色") private String role;
    @Schema(description = "消息内容") private String content;
    @Schema(description = "创建时间") private LocalDateTime createdAt;

    private MessageResponse() {}

    public static MessageResponse from(Message m) {
        MessageResponse r = new MessageResponse();
        r.id = m.getId();
        r.role = m.getRole().name().toLowerCase();
        r.content = m.getContent();
        r.createdAt = m.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
