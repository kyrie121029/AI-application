package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * RAG 检索单元 —— 由多个连续 DocumentSegment 组合/拆分产生。
 * <p>
 * 与 DocumentSegment 的区别：Segment 保留原始解析结构（页/段/行），
 * Chunk 是后续 Embedding / Vector Store / Retrieval 的最小检索单元。
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "document_chunks",
        uniqueConstraints = @UniqueConstraint(name = "uk_chunks_file_index", columnNames = {"file_id", "chunk_index"}))
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileRecord file;

    /** 文件内稳定序号（0, 1, 2...） */
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 字符数（近似统计，非 token 数） */
    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public DocumentChunk() {}

    public DocumentChunk(FileRecord file, int chunkIndex, String content, int characterCount) {
        this.file = file;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.characterCount = characterCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FileRecord getFile() { return file; }
    public void setFile(FileRecord file) { this.file = file; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getCharacterCount() { return characterCount; }
    public void setCharacterCount(int characterCount) { this.characterCount = characterCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
