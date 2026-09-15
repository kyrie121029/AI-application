-- V12：RAG 文档分块（Phase 7.1）—— Chunk + 来源映射（MySQL 版）

CREATE TABLE document_chunks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id         BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    content         MEDIUMTEXT NOT NULL,
    character_count INT NOT NULL,
    created_at      DATETIME(6),
    FOREIGN KEY (file_id) REFERENCES file_records(id) ON DELETE CASCADE,
    CONSTRAINT uk_chunks_file_index UNIQUE (file_id, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_document_chunks_file ON document_chunks(file_id);

CREATE TABLE document_chunk_sources (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_id      BIGINT NOT NULL,
    segment_id    BIGINT NOT NULL,
    segment_type  VARCHAR(20) NOT NULL,
    segment_index INT NOT NULL,
    start_offset  INT NOT NULL,
    end_offset    INT NOT NULL,
    position      INT NOT NULL,
    FOREIGN KEY (chunk_id) REFERENCES document_chunks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_chunk_sources_chunk ON document_chunk_sources(chunk_id);
CREATE INDEX idx_chunk_sources_segment ON document_chunk_sources(segment_id);
