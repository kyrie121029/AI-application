package com.example.demo.service;

import com.example.demo.dto.ChunkDraft;
import com.example.demo.enums.SegmentType;
import com.example.demo.model.DocumentSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultChunkingStrategy 单元测试 —— 自然边界优先 / 合并 / 超长拆分 / overlap / 来源追踪。
 */
class DefaultChunkingStrategyTest {

    private final DefaultChunkingStrategy strategy = new DefaultChunkingStrategy();

    private DocumentSegment seg(long id, SegmentType type, int index, String text) {
        DocumentSegment s = new DocumentSegment(null, type, index, text);
        s.setId(id);
        return s;
    }

    @Test
    @DisplayName("多个短 Segment 在预算内合并为单个 Chunk")
    void mergesShortSegments() {
        List<DocumentSegment> segs = List.of(
                seg(1L, SegmentType.PARAGRAPH, 0, "AAA"),
                seg(2L, SegmentType.PARAGRAPH, 1, "BBB"),
                seg(3L, SegmentType.PARAGRAPH, 2, "CCC"));

        List<ChunkDraft> chunks = strategy.chunk(segs, 100, 0);

        assertEquals(1, chunks.size());
        assertEquals("AAABBBCCC", chunks.get(0).content());
        assertEquals(3, chunks.get(0).sources().size());
    }

    @Test
    @DisplayName("加入下一个完整 Segment 会超 maxChars → 在 Segment 边界结束，不切开正常 Segment")
    void endsAtSegmentBoundary() {
        List<DocumentSegment> segs = List.of(
                seg(1L, SegmentType.PARAGRAPH, 0, "AAA"),
                seg(2L, SegmentType.PARAGRAPH, 1, "BBBB"));

        // 3 + 4 = 7 > 5 → chunk0 = "AAA"，chunk1 = "BBBB"（B 完整，不从中间切开）
        List<ChunkDraft> chunks = strategy.chunk(segs, 5, 0);

        assertEquals(2, chunks.size());
        assertEquals("AAA", chunks.get(0).content());
        assertEquals("BBBB", chunks.get(1).content());
    }

    @Test
    @DisplayName("自然边界优先：300/300/300 + maxChars=500 + overlap=100 → 三个段落均不被切开")
    void preservesNaturalBoundaries() {
        String a = "A".repeat(300), b = "B".repeat(300), c = "C".repeat(300);
        List<DocumentSegment> segs = List.of(
                seg(1L, SegmentType.PARAGRAPH, 0, a),
                seg(2L, SegmentType.PARAGRAPH, 1, b),
                seg(3L, SegmentType.PARAGRAPH, 2, c));

        List<ChunkDraft> chunks = strategy.chunk(segs, 500, 100);

        assertEquals(3, chunks.size());
        // chunk0 = A（完整）
        assertEquals(a, chunks.get(0).content());
        // chunk1 = A尾100(overlap) + B（B 完整）
        assertEquals(a.substring(200) + b, chunks.get(1).content());
        // chunk2 = B尾100(overlap) + C（C 完整）
        assertEquals(b.substring(200) + c, chunks.get(2).content());
    }

    @Test
    @DisplayName("超长 Segment 被正确拆分，不无限循环")
    void splitsOversizedSegment() {
        String longText = "X".repeat(250);
        List<DocumentSegment> segs = List.of(seg(1L, SegmentType.PARAGRAPH, 0, longText));

        List<ChunkDraft> chunks = strategy.chunk(segs, 100, 0);

        assertEquals(3, chunks.size());
        assertEquals(100, chunks.get(0).content().length());
        assertEquals(100, chunks.get(1).content().length());
        assertEquals(50, chunks.get(2).content().length());
    }

    @Test
    @DisplayName("超长 Segment 的 overlap：相邻 Chunk 存在重叠")
    void overlapWithinOversizedSegment() {
        String text = "0123456789ABCDEF";
        List<DocumentSegment> segs = List.of(seg(1L, SegmentType.LINE, 1, text));

        List<ChunkDraft> chunks = strategy.chunk(segs, 6, 2);

        assertEquals(4, chunks.size());
        assertEquals("012345", chunks.get(0).content());
        assertEquals("456789", chunks.get(1).content());
        assertEquals(chunks.get(0).content().substring(4), chunks.get(1).content().substring(0, 2));
    }

    @Test
    @DisplayName("overlap 切片的来源 offset 精确指向原始 Segment")
    void overlapSliceSourceOffsetCorrect() {
        // A="AAAA", B="BBBB", maxChars=6, overlap=2
        // chunk0="AAAA"; chunk1="AA"(A尾2 overlap)+"BBBB"
        List<DocumentSegment> segs = List.of(
                seg(1L, SegmentType.PARAGRAPH, 0, "AAAA"),
                seg(2L, SegmentType.PARAGRAPH, 1, "BBBB"));

        List<ChunkDraft> chunks = strategy.chunk(segs, 6, 2);

        assertEquals(2, chunks.size());
        assertEquals("AAAA", chunks.get(0).content());
        assertEquals("AABBBB", chunks.get(1).content());

        // chunk1 来源：A 的 [2,4) + B 的 [0,4)
        assertEquals(2, chunks.get(1).sources().size());
        assertEquals(1L, chunks.get(1).sources().get(0).segmentId());
        assertEquals(2, chunks.get(1).sources().get(0).startOffset());
        assertEquals(4, chunks.get(1).sources().get(0).endOffset());
        assertEquals(2L, chunks.get(1).sources().get(1).segmentId());
        assertEquals(0, chunks.get(1).sources().get(1).startOffset());
        assertEquals(4, chunks.get(1).sources().get(1).endOffset());
    }

    @Test
    @DisplayName("纯空白 Segment 被过滤，不产生无意义 Chunk")
    void filtersBlankSegments() {
        List<DocumentSegment> segs = List.of(
                seg(1L, SegmentType.PARAGRAPH, 0, "   \n\t "),
                seg(2L, SegmentType.PARAGRAPH, 1, "AAA"));

        List<ChunkDraft> chunks = strategy.chunk(segs, 100, 0);

        assertEquals(1, chunks.size());
        assertEquals("AAA", chunks.get(0).content());
        assertEquals(1, chunks.get(0).sources().size());
    }

    @Test
    @DisplayName("全空白输入返回空列表")
    void allBlankReturnsEmpty() {
        List<DocumentSegment> segs = List.of(seg(1L, SegmentType.PARAGRAPH, 0, "   "));
        assertTrue(strategy.chunk(segs, 100, 0).isEmpty());
    }

    @Test
    @DisplayName("跨页来源：两个 PAGE Segment 合为一个 Chunk，保留多个来源")
    void crossPageChunkKeepsMultipleSources() {
        List<DocumentSegment> segs = List.of(
                seg(1L, SegmentType.PAGE, 1, "page one text "),
                seg(2L, SegmentType.PAGE, 2, "page two text"));

        List<ChunkDraft> chunks = strategy.chunk(segs, 100, 0);

        assertEquals(1, chunks.size());
        assertEquals(2, chunks.get(0).sources().size());
        assertEquals(1, chunks.get(0).sources().get(0).segmentIndex());
        assertEquals(2, chunks.get(0).sources().get(1).segmentIndex());
    }

    @Test
    @DisplayName("顺序稳定：相同输入重复执行产生相同结果")
    void deterministicOrder() {
        List<DocumentSegment> segs = List.of(
                seg(1L, SegmentType.PARAGRAPH, 0, "AAA"),
                seg(2L, SegmentType.PARAGRAPH, 1, "BBB"));

        List<ChunkDraft> first = strategy.chunk(segs, 100, 0);
        List<ChunkDraft> second = strategy.chunk(segs, 100, 0);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).content(), second.get(i).content());
            assertEquals(first.get(i).sources(), second.get(i).sources());
        }
    }
}
