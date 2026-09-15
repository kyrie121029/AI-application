package com.example.demo.dto;

import com.example.demo.enums.SegmentType;

import java.util.List;

/**
 * Chunk 草稿 —— 分块算法的内部产物（持久化前），含内容与来源追踪。
 */
public record ChunkDraft(String content, List<SourceRef> sources) {

    /**
     * 单个来源：Chunk 内容在某个 Segment 原始文本中的字符区间。
     * startOffset / endOffset 用于后续 citation 精确定位。
     */
    public record SourceRef(Long segmentId, SegmentType type, int segmentIndex,
                            int startOffset, int endOffset) {
    }
}
