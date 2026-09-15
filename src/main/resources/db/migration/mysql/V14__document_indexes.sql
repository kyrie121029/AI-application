-- V14：文档索引状态（Phase 7.3）—— DocumentIndex（MySQL 版）
-- Qdrant 是从 DocumentChunk 重建的派生索引；本表记录索引过程与元数据

CREATE TABLE document_indexes (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id          BIGINT NOT NULL,
    status           VARCHAR(20) NOT NULL,
    embedding_model  VARCHAR(128),
    dimension        INT,
    index_version    VARCHAR(64),
    error_message    VARCHAR(500),
    updated_at       DATETIME(6),
    created_at       DATETIME(6),
    FOREIGN KEY (file_id) REFERENCES file_records(id) ON DELETE CASCADE,
    CONSTRAINT uk_document_indexes_file UNIQUE (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
