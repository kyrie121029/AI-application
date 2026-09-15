package com.example.demo.enums;

/**
 * 解析片段的来源类型 —— 对应文档的原始结构化粒度，供 Phase 7 citation 定位。
 * PAGE（PDF 页码） / PARAGRAPH（DOCX/TXT 段落） / LINE（TXT 行）
 */
public enum SegmentType {
    PAGE,
    PARAGRAPH,
    LINE
}
