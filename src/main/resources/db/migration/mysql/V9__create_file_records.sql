-- V9：文件上传 —— FileRecord 元数据表（MySQL 版）
-- 只存元数据；文件二进制保存在本地文件系统（storage.local.root-path）

CREATE TABLE file_records (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(255) NOT NULL,
    extension          VARCHAR(16),
    mime_type          VARCHAR(128),
    size_bytes         BIGINT NOT NULL,
    sha256             VARCHAR(64) NOT NULL,
    created_at         DATETIME(6),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- owner 列表查询 + sha256 重复检测 + storageKey 唯一定位
CREATE INDEX idx_file_records_user ON file_records(user_id);
CREATE INDEX idx_file_records_sha256 ON file_records(sha256);
CREATE UNIQUE INDEX uk_file_records_storage_key ON file_records(storage_key);
