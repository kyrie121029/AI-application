package com.example.demo.service;

import com.example.demo.exception.InvalidFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片安全校验测试 —— 扩展名 + MIME + magic number 三者一致。
 */
class FileContentValidatorImageTest {

    private final FileContentValidator validator = new FileContentValidator();

    @Test
    @DisplayName("正常 JPEG 通过")
    void validJpeg() {
        MockMultipartFile f = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        assertEquals("jpg", validator.validate(f));
    }

    @Test
    @DisplayName("正常 PNG 通过")
    void validPng() {
        MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        assertEquals("png", validator.validate(f));
    }

    @Test
    @DisplayName("正常 WEBP 通过")
    void validWebp() {
        MockMultipartFile f = new MockMultipartFile("file", "a.webp", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
        assertEquals("webp", validator.validate(f));
    }

    @Test
    @DisplayName("扩展名伪装：.jpg 文件名 + PNG 内容 → 拒绝")
    void jpgNameWithPngContentRejected() {
        MockMultipartFile f = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        assertThrows(InvalidFileException.class, () -> validator.validate(f));
    }

    @Test
    @DisplayName("声明 MIME 与扩展名不一致 → 拒绝")
    void mismatchedDeclaredMimeRejected() {
        MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/jpeg",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        assertThrows(InvalidFileException.class, () -> validator.validate(f));
    }

    @Test
    @DisplayName("magic number 不匹配 → 拒绝（.png 文件名 + 文本内容）")
    void textContentAsPngRejected() {
        MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png",
                "hello not an image".getBytes());
        assertThrows(InvalidFileException.class, () -> validator.validate(f));
    }

    @Test
    @DisplayName("isImageExtension 判断")
    void isImageExtension() {
        assertTrue(FileContentValidator.isImageExtension("jpg"));
        assertTrue(FileContentValidator.isImageExtension("webp"));
        assertFalse(FileContentValidator.isImageExtension("pdf"));
        assertFalse(FileContentValidator.isImageExtension(null));
    }
}
