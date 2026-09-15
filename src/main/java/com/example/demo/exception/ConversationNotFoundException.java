package com.example.demo.exception;

/**
 * 会话不存在异常 —— 查询不存在的会话 ID 时抛出
 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(Long conversationId) {
        super("对话不存在: id=" + conversationId);
    }
}
