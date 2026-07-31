-- ============================================================
-- V2：用户表 + 任务关联（MySQL 版）
-- ============================================================

CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(20) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 给 tasks 加 user_id 外键
ALTER TABLE tasks ADD COLUMN user_id BIGINT;
ALTER TABLE tasks ADD CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES users(id);