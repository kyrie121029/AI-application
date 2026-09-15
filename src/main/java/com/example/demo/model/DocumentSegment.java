package com.example.demo.model;

import com.example.demo.enums.SegmentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 文档解析片段 —— 一个文件解析出的结构化内容（页面/段落/行）。
 * <p>
 * 保存"解析后的原始结构化内容"，含来源序号（页码/段落/行号），
 * 供 Phase 7 Chunk / Embedding / citation 使用；这里不做真正的 Chunk 切分。
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "document_segments")
public class DocumentSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileRecord file;

    /** 片段类型：PAGE / PARAGRAPH / LINE */
    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false)
    private SegmentType segmentType;

    /** 来源序号：页码 / 段落序号 / 行号 */
    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public DocumentSegment() {}

    public DocumentSegment(FileRecord file, SegmentType segmentType, int segmentIndex, String text) {
        this.file = file;
        this.segmentType = segmentType;
        this.segmentIndex = segmentIndex;
        this.text = text;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FileRecord getFile() { return file; }
    public void setFile(FileRecord file) { this.file = file; }
    public SegmentType getSegmentType() { return segmentType; }
    public void setSegmentType(SegmentType segmentType) { this.segmentType = segmentType; }
    public int getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(int segmentIndex) { this.segmentIndex = segmentIndex; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
