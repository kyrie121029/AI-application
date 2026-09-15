package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.exception.DocumentParseException;

import java.io.InputStream;

/**
 * 文档解析器抽象 —— 按扩展名解析文件内容为结构化片段。
 * <p>
 * 契约：
 *   - parse 只消费 InputStream，不负责关闭它（生命周期由调用方 FileParseWorker 持有并关闭）；
 *   - 底层 PDFBox / POI / 编码异常统一包装为 DocumentParseException。
 */
public interface DocumentParser {

    /** 支持的文件扩展名（小写，如 pdf / docx / txt） */
    String supportedExtension();

    /** 解析 InputStream 为结构化片段；不关闭传入的流 */
    ParsedDocument parse(InputStream inputStream) throws DocumentParseException;
}
