-- V13：Embedding（Phase 7.2）—— AIUsageLog 增加调用类型（MySQL 版）
-- 可空：旧数据未分类（null），不误导语义
ALTER TABLE ai_usage_logs ADD COLUMN call_type VARCHAR(20);
