package com.example.demo.service;

import com.example.demo.dto.ChunkDraft;
import com.example.demo.model.DocumentSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认分块策略 —— 自然边界优先的贪心累积。
 * <p>
 * 原则：
 *   1. 优先以完整 DocumentSegment 为单元组合 Chunk；
 *   2. 若「当前累积 + 下一个完整 Segment」仍 ≤ maxChars，则合并；
 *   3. 否则结束当前 Chunk，从上一 Chunk 尾部 overlapChars 字符（可能切到某 Segment 尾部）作为新 Chunk 前缀；
 *   4. 仅当单个 Segment 本身超过 maxChars 时，才在该 Segment 内部按窗口拆分（overlap 同样适用）。
 * <p>
 * 效果：正常短 Segment 不会被固定窗口从中间切开；来源 offset 精确指向原始 Segment 文本（无清洗，offset 不失真）。
 */
@Component
public class DefaultChunkingStrategy implements ChunkingStrategy {

    @Override
    public List<ChunkDraft> chunk(List<DocumentSegment> segments, int maxChars, int overlapChars) {
        List<ChunkDraft> drafts = new ArrayList<>();
        List<Piece> buffer = new ArrayList<>();
        int bufferLen = 0;

        for (DocumentSegment segment : segments) {
            String text = segment.getText();
            if (text == null || text.isBlank()) continue;

            if (text.length() > maxChars) {
                // 超长 Segment：先结束当前累积，再内部拆分
                if (!buffer.isEmpty()) {
                    drafts.add(buildChunk(buffer));
                    buffer.clear();
                    bufferLen = 0;
                }
                splitOversized(segment, text, maxChars, overlapChars, drafts);
                continue;
            }

            if (bufferLen + text.length() <= maxChars) {
                buffer.add(new Piece(segment, 0, text.length(), text));
                bufferLen += text.length();
            } else {
                // 加入会超 → 在 Segment 边界结束当前 Chunk，保留尾部 overlap 作新 Chunk 前缀
                drafts.add(buildChunk(buffer));
                buffer = extractOverlap(buffer, overlapChars);
                bufferLen = lengthOf(buffer);
                if (bufferLen + text.length() <= maxChars) {
                    buffer.add(new Piece(segment, 0, text.length(), text));
                    bufferLen += text.length();
                } else {
                    // overlap 前缀与当前 Segment 合并仍超（罕见）：overlap 单独成 Chunk，Segment 重新开始
                    drafts.add(buildChunk(buffer));
                    buffer.clear();
                    buffer.add(new Piece(segment, 0, text.length(), text));
                    bufferLen = text.length();
                }
            }
        }

        if (!buffer.isEmpty()) {
            drafts.add(buildChunk(buffer));
        }
        return drafts;
    }

    /** 超长 Segment 内部按 maxChars 窗口拆分（overlap 回退）；来源是同一 Segment 的不同字符区间 */
    private void splitOversized(DocumentSegment segment, String text,
                                int maxChars, int overlapChars, List<ChunkDraft> drafts) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            String content = text.substring(start, end);
            drafts.add(new ChunkDraft(content, List.of(new ChunkDraft.SourceRef(
                    segment.getId(), segment.getSegmentType(), segment.getSegmentIndex(), start, end))));
            if (end >= text.length()) break;
            start = end - overlapChars;
        }
    }

    /** 从 buffer 末尾向前取 overlapChars 字符（可能切到某个 Segment 尾部），保持从左到右顺序 */
    private List<Piece> extractOverlap(List<Piece> buffer, int overlapChars) {
        List<Piece> result = new ArrayList<>();
        int remaining = overlapChars;
        for (int i = buffer.size() - 1; i >= 0 && remaining > 0; i--) {
            Piece p = buffer.get(i);
            int take = Math.min(p.text.length(), remaining);
            int localStart = p.localStart + p.text.length() - take;
            result.add(0, new Piece(p.segment, localStart, p.localEnd, p.text.substring(p.text.length() - take)));
            remaining -= take;
        }
        return result;
    }

    private ChunkDraft buildChunk(List<Piece> buffer) {
        StringBuilder content = new StringBuilder();
        List<ChunkDraft.SourceRef> sources = new ArrayList<>();
        for (Piece p : buffer) {
            content.append(p.text);
            sources.add(new ChunkDraft.SourceRef(
                    p.segment.getId(), p.segment.getSegmentType(), p.segment.getSegmentIndex(),
                    p.localStart, p.localEnd));
        }
        return new ChunkDraft(content.toString(), sources);
    }

    private int lengthOf(List<Piece> pieces) {
        int total = 0;
        for (Piece p : pieces) total += p.text.length();
        return total;
    }

    /** 一个 Segment 文本在 Chunk 中的切片：[localStart, localEnd) */
    private record Piece(DocumentSegment segment, int localStart, int localEnd, String text) {
    }
}
