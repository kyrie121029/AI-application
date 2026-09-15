package com.example.demo.model;

import com.example.demo.enums.IndexStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 文档索引状态 —— 一个 FileRecord 对应一条（file_id 唯一），记录索引元数据与状态。
 * <p>
 * Qdrant 是可从 MySQL DocumentChunk 重建的派生索引，本表是索引过程的事实记录。
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "document_indexes")
public class DocumentIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private FileRecord file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IndexStatus status = IndexStatus.PENDING;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    private Integer dimension;

    @Column(name = "index_version", length = 64)
    private String indexVersion;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public DocumentIndex() {}

    public DocumentIndex(FileRecord file) {
        this.file = file;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FileRecord getFile() { return file; }
    public void setFile(FileRecord file) { this.file = file; }
    public IndexStatus getStatus() { return status; }
    public void setStatus(IndexStatus status) { this.status = status; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getDimension() { return dimension; }
    public void setDimension(Integer dimension) { this.dimension = dimension; }
    public String getIndexVersion() { return indexVersion; }
    public void setIndexVersion(String indexVersion) { this.indexVersion = indexVersion; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
