-- V8：聊天增强 —— requestId 幂等 + AI 使用日志扩展（MySQL 版）

-- 1. messages 增加 request_id，用于重复提交去重
ALTER TABLE messages ADD COLUMN request_id VARCHAR(64);
CREATE UNIQUE INDEX uk_messages_conv_request ON messages(conversation_id, request_id);

-- 2. ai_usage_logs 增加流式日志字段
ALTER TABLE ai_usage_logs ADD COLUMN conversation_id BIGINT;
ALTER TABLE ai_usage_logs ADD COLUMN first_token_latency_ms BIGINT DEFAULT 0;
ALTER TABLE ai_usage_logs ADD COLUMN retry_count INT DEFAULT 0;
ALTER TABLE ai_usage_logs ADD COLUMN failed_before_first_token TINYINT(1) DEFAULT 0;
