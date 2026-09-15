package com.example.demo.service;

import com.example.demo.exception.InvalidFileException;
import com.example.demo.util.TextEncodingUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件内容校验 —— 扩展名 allowlist + 声明 MIME + 真实文件特征 三者一致才放行。
 * <p>
 * 轻量实现，不引入第三方 MIME 检测库：
 *   - PDF   ：检查文件头 %PDF-
 *   - DOCX  ：ZIP 结构 + 存在 [Content_Types].xml 或 word/ 条目（Office Open XML 特征）
 *   - TXT   ：前 4KB 不含 NUL 且可解码为文本（UTF-8，兼容 GBK）
 */
@Component
public class FileContentValidator {

    /** Phase 6 Step 1/4 允许的扩展名（allowlist，不用 blacklist） */
    public static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "docx", "txt", "jpg", "jpeg", "png", "webp");

    /** 图片扩展名（用于图片分析分流） */
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /** 扩展名 → 规范化 MIME（入库时使用，不信任客户端声明） */
    private static final Map<String, String> EXTENSION_MIME = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int TXT_PROBE_BYTES = 4096;

    /**
     * 校验 MultipartFile，通过则返回规范化扩展名（小写）。
     * 任一环节失败抛 InvalidFileException（→ HTTP 400）。
     */
    public String validate(MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException("不支持的文件类型: " + extension + "（仅允许 pdf/docx/txt）");
        }

        // 声明 MIME 与扩展名一致（客户端可伪造，仅作第一道检查）
        String expectedMime = EXTENSION_MIME.get(extension);
        String declared = file.getContentType();
        if (declared != null && !declared.isBlank() && !declared.equalsIgnoreCase(expectedMime)) {
            throw new InvalidFileException("文件 MIME 类型(" + declared + ")与扩展名 ." + extension + " 不一致");
        }

        // 真实文件特征（最终防线，不依赖客户端声明）
        switch (extension) {
            case "pdf" -> verifyPdfHeader(file);
            case "docx" -> verifyDocxStructure(file);
            case "txt" -> verifyTextContent(file);
            case "jpg", "jpeg", "png", "webp" -> verifyImageSignature(file, extension);
            default -> throw new InvalidFileException("不支持的文件类型: " + extension);
        }
        return extension;
    }

    /** 扩展名 → 规范化 MIME（供 FileRecord 入库） */
    public static String mimeTypeOf(String extension) {
        return EXTENSION_MIME.get(extension);
    }

    /** 是否为图片扩展名（用于图片分析分流） */
    public static boolean isImageExtension(String extension) {
        return extension != null && IMAGE_EXTENSIONS.contains(extension);
    }

    /** 从原始文件名提取小写扩展名（去掉路径成分） */
    static String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "";
        String name = originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private void verifyPdfHeader(MultipartFile file) {
        byte[] header = readPrefix(file, PDF_HEADER.length);
        for (int i = 0; i < PDF_HEADER.length; i++) {
            if (i >= header.length || header[i] != PDF_HEADER[i]) {
                throw new InvalidFileException("文件内容不是合法的 PDF（缺少 %PDF- 文件头）");
            }
        }
    }

    /** DOCX = ZIP 容器 + OOXML 结构特征条目（不能只因为是 ZIP 就放行） */
    private void verifyDocxStructure(MultipartFile file) {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("[Content_Types].xml") || name.startsWith("word/")) {
                    return; // 具备 Office Open XML 结构特征
                }
            }
        } catch (IOException e) {
            throw new InvalidFileException("文件内容不是合法的 DOCX（无法读取 ZIP 结构）");
        }
        throw new InvalidFileException("文件内容不是合法的 DOCX（缺少 OOXML 结构特征）");
    }

    /**
     * TXT：前 4KB 不含 NUL 且控制字符占比低（排除二进制/图片/压缩包），
     * 并要求能严格解码（UTF-8，兼容 GBK 文本）
     */
    private void verifyTextContent(MultipartFile file) {
        byte[] probe = readPrefix(file, TXT_PROBE_BYTES);
        if (probe.length == 0) {
            throw new InvalidFileException("文件内容不是文本文件（内容为空）");
        }
        int controlChars = 0;
        for (byte b : probe) {
            // 注意：byte 是有符号的，必须转无符号后再比较（否则中文等 >0x7F 字节全部误判）
            int u = b & 0xFF;
            if (u == 0) {
                throw new InvalidFileException("文件内容不是文本文件（包含二进制数据）");
            }
            if (u < 0x20 && u != '\t' && u != '\n' && u != '\r') {
                controlChars++;
            }
        }
        if (controlChars * 100 / probe.length > 2) {
            throw new InvalidFileException("文件内容不是文本文件（控制字符占比过高）");
        }
        if (TextEncodingUtil.detectCharset(probe) == null) {
            throw new InvalidFileException("文件内容不是文本文件（无法按 UTF-8/GBK 解码）");
        }
    }

    private byte[] readPrefix(MultipartFile file, int maxBytes) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(maxBytes);
        } catch (IOException e) {
            throw new InvalidFileException("无法读取文件内容");
        }
    }

    /** 图片 magic number 校验（JPEG / PNG / WEBP） */
    private void verifyImageSignature(MultipartFile file, String extension) {
        byte[] header = readPrefix(file, 12);
        boolean valid = switch (extension) {
            case "jpg", "jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png" -> header.length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            case "webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
        if (!valid) {
            throw new InvalidFileException("文件内容与图片类型 ." + extension + " 不匹配");
        }
    }
}
