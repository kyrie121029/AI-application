-- V4：AI 使用日志增加 user_id 和 success 字段（MySQL 版）

ALTER TABLE ai_usage_logs ADD COLUMN user_id BIGINT;
ALTER TABLE ai_usage_logs ADD COLUMN success TINYINT(1) DEFAULT 1;
