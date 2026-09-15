package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.enums.SegmentType;
import com.example.demo.exception.DocumentParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三个 DocumentParser 的真实解析测试（不 mock 第三方库）。
 */
class DocumentParserTest {

    private final PdfDocumentParser pdfParser = new PdfDocumentParser();
    private final DocxDocumentParser docxParser = new DocxDocumentParser();
    private final TxtDocumentParser txtParser = new TxtDocumentParser();

    // ==================== PDF ====================

    private byte[] makePdf(String... pageTexts) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (pageText != null) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 12);
                        cs.newLineAtOffset(100, 700);
                        cs.showText(pageText);
                        cs.endText();
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("PDF：正常文本解析，保留 pageNumber")
    void parsesSinglePagePdf() throws Exception {
        byte[] pdf = makePdf("Hello PDF");
        ParsedDocument doc = pdfParser.parse(new ByteArrayInputStream(pdf));

        assertEquals(1, doc.segments().size());
        assertEquals(SegmentType.PAGE, doc.segments().get(0).type());
        assertEquals(1, doc.segments().get(0).index());
        assertTrue(doc.segments().get(0).text().contains("Hello PDF"));
    }

    @Test
    @DisplayName("PDF：多页保留各自 pageNumber")
    void parsesMultiPagePdfWithPageNumbers() throws Exception {
        byte[] pdf = makePdf("page one", "page two", "page three");
        ParsedDocument doc = pdfParser.parse(new ByteArrayInputStream(pdf));

        assertEquals(3, doc.segments().size());
        assertEquals(List.of(1, 2, 3),
                doc.segments().stream().map(ParsedDocument.ParsedSegment::index).toList());
    }

    @Test
    @DisplayName("PDF：无文本层不崩溃，返回空片段")
    void emptyPdfReturnsNoSegments() throws Exception {
        byte[] pdf = makePdf(new String[0]);
        ParsedDocument doc = pdfParser.parse(new ByteArrayInputStream(pdf));
        assertEquals(0, doc.segments().size());
    }

    @Test
    @DisplayName("PDF：非法内容抛 DocumentParseException")
    void invalidPdfThrows() {
        byte[] notPdf = "this is not a pdf".getBytes(StandardCharsets.UTF_8);
        assertThrows(DocumentParseException.class,
                () -> pdfParser.parse(new ByteArrayInputStream(notPdf)));
    }

    // ==================== DOCX ====================

    private byte[] makeDocx(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("DOCX：按段落顺序解析并保留 paragraphIndex")
    void parsesDocxParagraphsInOrder() throws Exception {
        byte[] docx = makeDocx("第一段", "第二段", "第三段");
        ParsedDocument doc = docxParser.parse(new ByteArrayInputStream(docx));

        assertEquals(3, doc.segments().size());
        assertEquals(SegmentType.PARAGRAPH, doc.segments().get(0).type());
        assertEquals("第一段", doc.segments().get(0).text());
        assertEquals("第三段", doc.segments().get(2).text());
        assertEquals(List.of(0, 1, 2),
                doc.segments().stream().map(ParsedDocument.ParsedSegment::index).toList());
    }

    @Test
    @DisplayName("DOCX：非法内容抛 DocumentParseException")
    void invalidDocxThrows() {
        byte[] notDocx = "plain text not zip".getBytes(StandardCharsets.UTF_8);
        assertThrows(DocumentParseException.class,
                () -> docxParser.parse(new ByteArrayInputStream(notDocx)));
    }

    // ==================== TXT ====================

    @Test
    @DisplayName("TXT：UTF-8 按行解析并保留行号")
    void parsesUtf8TxtByLine() {
        byte[] txt = "line one\nline two\nline three".getBytes(StandardCharsets.UTF_8);
        ParsedDocument doc = txtParser.parse(new ByteArrayInputStream(txt));

        assertEquals(3, doc.segments().size());
        assertEquals(SegmentType.LINE, doc.segments().get(0).type());
        assertEquals(1, doc.segments().get(0).index());
        assertEquals("line two", doc.segments().get(1).text());
    }

    @Test
    @DisplayName("TXT：中文 UTF-8 正确解析")
    void parsesChineseUtf8Txt() {
        byte[] txt = "第一行\n第二行".getBytes(StandardCharsets.UTF_8);
        ParsedDocument doc = txtParser.parse(new ByteArrayInputStream(txt));

        assertEquals(2, doc.segments().size());
        assertEquals("第一行", doc.segments().get(0).text());
    }

    @Test
    @DisplayName("TXT：GBK 编码正确解析")
    void parsesGbkTxt() throws Exception {
        byte[] txt = "第一行GBK\n第二行GBK".getBytes("GBK");
        ParsedDocument doc = txtParser.parse(new ByteArrayInputStream(txt));

        assertEquals(2, doc.segments().size());
        assertEquals("第一行GBK", doc.segments().get(0).text());
    }

    @Test
    @DisplayName("TXT：无法识别的编码抛 DocumentParseException")
    void unreadableEncodingThrows() {
        // 非法 UTF-8 且非法 GBK 的字节序列（0xFF 0xFF 0xFF 无效起始）
        byte[] bad = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertThrows(DocumentParseException.class,
                () -> txtParser.parse(new ByteArrayInputStream(bad)));
    }
}
