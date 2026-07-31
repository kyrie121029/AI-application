-- ============================================================
-- V1：MySQL 版建表 — tasks（任务表）
-- ============================================================

CREATE TABLE tasks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    task_type   VARCHAR(255),
    input_text  MEDIUMTEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result      MEDIUMTEXT,
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;