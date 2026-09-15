package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.enums.SegmentType;
import com.example.demo.exception.DocumentParseException;
import com.example.demo.util.TextEncodingUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT 解析器 —— 自动检测 UTF-8 / GBK 编码，按行提取文本并保留行号。
 * <p>
 * 复用 TextEncodingUtil 的编码检测（与 FileContentValidator 同一套规则）。
 * 文件大小已在上传层限制（≤10MB），此处整读是有界的，不会无上限读入。
 */
@Component
public class TxtDocumentParser implements DocumentParser {

    @Override
    public String supportedExtension() {
        return "txt";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream) throws DocumentParseException {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new DocumentParseException("TXT 读取失败", e);
        }

        Charset charset = TextEncodingUtil.detectCharset(bytes);
        if (charset == null) {
            throw new DocumentParseException("无法识别 TXT 文件编码（仅支持 UTF-8 / GBK）");
        }

        List<ParsedDocument.ParsedSegment> segments = new ArrayList<>();
        // \R 匹配 \n、\r\n、\r，保持顺序
        String[] lines = new String(bytes, charset).split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isBlank()) {
                segments.add(new ParsedDocument.ParsedSegment(SegmentType.LINE, i + 1, line.strip()));
            }
        }
        return new ParsedDocument(segments);
    }
}
