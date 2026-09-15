-- V11：图片分析（Phase 6.4）—— 图片元数据 + 图片分析结果 + AI 日志关联文件（MySQL 版）

-- 1. file_records 增加图片宽高
ALTER TABLE file_records ADD COLUMN image_width INT;
ALTER TABLE file_records ADD COLUMN image_height INT;

-- 2. ai_usage_logs 增加 file_id（图片分析场景）
ALTER TABLE ai_usage_logs ADD COLUMN file_id BIGINT;

-- 3. 图片分析结果表（一个文件一条，file_id 唯一）
CREATE TABLE image_analyses (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id          BIGINT NOT NULL,
    status           VARCHAR(20) NOT NULL,
    failure_reason   VARCHAR(500),
    model_name       VARCHAR(128),
    summary          TEXT,
    scene            TEXT,
    text_content     TEXT,
    objects_json     TEXT,
    risk_flags_json  TEXT,
    analyzed_at      DATETIME(6),
    created_at       DATETIME(6),
    FOREIGN KEY (file_id) REFERENCES file_records(id) ON DELETE CASCADE,
    CONSTRAINT uk_image_analyses_file UNIQUE (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
