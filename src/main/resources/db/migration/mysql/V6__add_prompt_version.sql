-- V6：AI 使用日志增加 prompt_version 字段（MySQL 版）

ALTER TABLE ai_usage_logs ADD COLUMN prompt_version VARCHAR(50);
