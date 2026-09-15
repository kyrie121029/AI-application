package com.example.demo.service;

import com.example.demo.config.StorageProperties;
import com.example.demo.exception.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalStorageService 单元测试 —— 使用 @TempDir 临时目录，不污染真实文件系统。
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getLocal().setRootPath(tempDir.toString());
        service = new LocalStorageService(props);
    }

    @Test
    @DisplayName("正常保存：物理文件写入 root 之下")
    void storeWritesFileUnderRoot() throws Exception {
        service.store("1/2026/08/a.txt", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 5);

        Path file = tempDir.resolve("1/2026/08/a.txt");
        assertTrue(Files.exists(file));
        assertEquals("hello", Files.readString(file));
    }

    @Test
    @DisplayName("正常读取：内容与写入一致")
    void loadReturnsStoredContent() throws Exception {
        service.store("1/2026/08/b.txt", new ByteArrayInputStream("hello storage".getBytes(StandardCharsets.UTF_8)), 13);

        String content = StreamUtils.copyToString(
                service.load("1/2026/08/b.txt").getInputStream(), StandardCharsets.UTF_8);

        assertEquals("hello storage", content);
    }

    @Test
    @DisplayName("删除：删除后物理文件不存在，重复删除幂等")
    void deleteRemovesFileAndIsIdempotent() throws Exception {
        service.store("1/2026/08/c.txt", new ByteArrayInputStream("x".getBytes()), 1);

        service.delete("1/2026/08/c.txt");
        assertFalse(Files.exists(tempDir.resolve("1/2026/08/c.txt")));

        assertDoesNotThrow(() -> service.delete("1/2026/08/c.txt")); // 幂等
    }

    @Test
    @DisplayName("读取不存在的 storageKey → FileStorageException")
    void loadMissingKeyThrows() {
        assertThrows(FileStorageException.class, () -> service.load("no/such/file.txt"));
    }

    @Test
    @DisplayName("路径穿越防护：../ 的 storageKey 被拒绝")
    void rejectsPathTraversalStore() {
        assertThrows(FileStorageException.class,
                () -> service.store("../evil.txt", new ByteArrayInputStream("x".getBytes()), 1));
        assertThrows(FileStorageException.class, () -> service.load("../../evil.txt"));
        assertThrows(FileStorageException.class, () -> service.delete("..\\..\\evil.txt"));
    }

    @Test
    @DisplayName("路径穿越防护：绝对路径 storageKey 被拒绝")
    void rejectsAbsolutePath() {
        String absolute = tempDir.getParent().resolve("outside.txt").toString();
        assertThrows(FileStorageException.class,
                () -> service.store(absolute, new ByteArrayInputStream("x".getBytes()), 1));
    }

    @Test
    @DisplayName("空 storageKey 被拒绝")
    void rejectsBlankKey() {
        assertThrows(FileStorageException.class,
                () -> service.store("  ", new ByteArrayInputStream("x".getBytes()), 1));
    }
}
