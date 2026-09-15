package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.enums.SegmentType;
import com.example.demo.exception.DocumentParseException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX 解析器 —— 基于 Apache POI，按段落顺序提取正文文本并保留 paragraphIndex。
 * <p>
 * 当前只解析正文段落，暂不处理表格/图片/页眉页脚完整结构（保持轻量）。
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public String supportedExtension() {
        return "docx";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream) throws DocumentParseException {
        List<ParsedDocument.ParsedSegment> segments = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText();
                if (text != null && !text.isBlank()) {
                    segments.add(new ParsedDocument.ParsedSegment(SegmentType.PARAGRAPH, i, text.strip()));
                }
            }
        } catch (IOException e) {
            throw new DocumentParseException("DOCX 解析失败", e);
        } catch (Exception e) {
            throw new DocumentParseException("DOCX 解析失败（文件结构异常）", e);
        }
        return new ParsedDocument(segments);
    }
}
