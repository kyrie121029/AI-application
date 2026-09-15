package com.example.demo.service;

import com.example.demo.exception.DocumentParseException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 解析器注册表 —— 注入全部 DocumentParser 实现，按扩展名索引。
 * 编排层只通过本注册表获取 Parser，不依赖具体解析器。
 */
@Component
public class DocumentParserRegistry {

    private final Map<String, DocumentParser> parsers;

    public DocumentParserRegistry(List<DocumentParser> parserList) {
        this.parsers = parserList.stream()
                .collect(Collectors.toMap(DocumentParser::supportedExtension, p -> p));
    }

    public DocumentParser get(String extension) {
        DocumentParser parser = parsers.get(extension);
        if (parser == null) {
            throw new DocumentParseException("不支持解析的文件类型: " + extension);
        }
        return parser;
    }
}
