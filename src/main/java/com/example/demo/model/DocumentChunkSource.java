package com.example.demo.model;

import com.example.demo.enums.SegmentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/**
 * Chunk 来源映射 —— 表达一个 Chunk 与一个或多个 DocumentSegment 的来源关系。
 * <p>
 * 一个 Chunk 可能横跨多个 Segment（甚至多个页面/段落），因此用关联表而非 chunk → 单 segmentId。
 * startOffset / endOffset 是该 Chunk 在此 Segment 原始文本中的字符偏移（用于精确 citation）。
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "document_chunk_sources")
public class DocumentChunkSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", nullable = false)
    private DocumentChunk chunk;

    /** 来源 Segment ID（不设 FK，因为 Segment 会随重新解析整体替换） */
    @Column(name = "segment_id", nullable = false)
    private Long segmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false, length = 20)
    private SegmentType segmentType;

    /** 来源序号：页码 / 段落序号 / 行号 */
    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    /** Chunk 内容在此 Segment 文本中的起始字符偏移 */
    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    /** Chunk 内容在此 Segment 文本中的结束字符偏移（不含） */
    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    /** 该来源在 Chunk 内的顺序 */
    @Column(name = "position", nullable = false)
    private int position;

    public DocumentChunkSource() {}

    public DocumentChunkSource(DocumentChunk chunk, Long segmentId, SegmentType segmentType,
                               int segmentIndex, int startOffset, int endOffset, int position) {
        this.chunk = chunk;
        this.segmentId = segmentId;
        this.segmentType = segmentType;
        this.segmentIndex = segmentIndex;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.position = position;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DocumentChunk getChunk() { return chunk; }
    public void setChunk(DocumentChunk chunk) { this.chunk = chunk; }
    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }
    public SegmentType getSegmentType() { return segmentType; }
    public void setSegmentType(SegmentType segmentType) { this.segmentType = segmentType; }
    public int getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(int segmentIndex) { this.segmentIndex = segmentIndex; }
    public int getStartOffset() { return startOffset; }
    public void setStartOffset(int startOffset) { this.startOffset = startOffset; }
    public int getEndOffset() { return endOffset; }
    public void setEndOffset(int endOffset) { this.endOffset = endOffset; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
