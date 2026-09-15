package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.enums.SegmentType;
import com.example.demo.exception.DocumentParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析器 —— 基于 Apache PDFBox，逐页提取文本并保留 pageNumber。
 * <p>
 * 只解析文本型 PDF，不做 OCR；扫描版无文本层时返回空片段列表（SUCCESS，不调用视觉模型）。
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public String supportedExtension() {
        return "pdf";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream) throws DocumentParseException {
        List<ParsedDocument.ParsedSegment> segments = new ArrayList<>();
        try (PDDocument document = PDDocument.load(inputStream)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    segments.add(new ParsedDocument.ParsedSegment(SegmentType.PAGE, page, text.strip()));
                }
            }
        } catch (IOException e) {
            throw new DocumentParseException("PDF 解析失败", e);
        }
        return new ParsedDocument(segments);
    }
}
