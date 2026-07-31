-- ============================================================
-- V3：AI 调用日志表（MySQL 版）
-- ============================================================

CREATE TABLE ai_usage_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT,
    provider        VARCHAR(20) NOT NULL,
    model_name      VARCHAR(50),
    tokens_used     INT DEFAULT 0,
    elapsed_ms      BIGINT DEFAULT 0,
    request_preview MEDIUMTEXT,
    created_at      DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
