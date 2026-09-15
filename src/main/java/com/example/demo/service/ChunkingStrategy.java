package com.example.demo.service;

import com.example.demo.dto.ChunkDraft;
import com.example.demo.model.DocumentSegment;

import java.util.List;

/**
 * 分块策略抽象 —— DocumentSegment 列表 → ChunkDraft 列表。
 * <p>
 * 纯函数：不访问数据库，同一输入 + 同一配置产出相同结果。
 */
public interface ChunkingStrategy {

    /**
     * @param segments     已按原始文档顺序排序的 Segment
     * @param maxChars     单个 Chunk 最大字符数
     * @param overlapChars 相邻 Chunk 重叠字符数（>=0 且 < maxChars）
     */
    List<ChunkDraft> chunk(List<DocumentSegment> segments, int maxChars, int overlapChars);
}
