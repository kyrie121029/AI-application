-- V5：AI 使用日志增强（MySQL 版）

ALTER TABLE ai_usage_logs ADD COLUMN input_tokens INT DEFAULT 0;
ALTER TABLE ai_usage_logs ADD COLUMN output_tokens INT DEFAULT 0;
ALTER TABLE ai_usage_logs ADD COLUMN error_type VARCHAR(20);
ALTER TABLE ai_usage_logs DROP COLUMN tokens_used;
