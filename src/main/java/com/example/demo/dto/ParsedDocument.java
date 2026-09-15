package com.example.demo.dto;

import com.example.demo.enums.SegmentType;

import java.util.List;

/**
 * 解析结果（非持久化 DTO）—— 一个文件解析出的结构化片段集合。
 * <p>
 * 这是"解析后的原始结构化内容"，尚未做 Chunk 切分（那是 Phase 7 的职责）。
 */
public record ParsedDocument(List<ParsedSegment> segments) {

    /** 单个解析片段：来源类型 + 序号（页码/段落/行号）+ 文本 */
    public record ParsedSegment(SegmentType type, int index, String text) {
    }
}
