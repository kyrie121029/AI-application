package com.example.demo.service;

import com.example.demo.config.StorageProperties;
import com.example.demo.dto.FileResponse;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.FileTooLargeException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileService 单元测试。
 * FileContentValidator 使用真实实现（@Spy），只 mock 存储与数据库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private StorageService storageService;
    @Mock private StorageProperties storageProperties;
    @Mock private FileParseService fileParseService;
    @Mock private ImageAnalysisService imageAnalysisService;
    @Mock private DocumentIndexService documentIndexService;
    @Spy private FileContentValidator contentValidator;
    @InjectMocks private FileService fileService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = new User(1L, "alice", "pwd");
        when(storageProperties.getMaxFileSize()).thenReturn(DataSize.ofMegabytes(10));
        // save 回填 id，模拟数据库自增
        when(fileRepository.save(any(FileRecord.class))).thenAnswer(inv -> {
            FileRecord r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
    }

    private MockMultipartFile pdf(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    private byte[] minimalDocx() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return baos.toByteArray();
    }

    // ==================== 正常上传 ====================

    @Test
    @DisplayName("正常 PDF 上传：校验通过、返回元数据、FileRecord 正确保存")
    void shouldUploadValidPdf() {
        byte[] content = "%PDF-1.4\n1 0 obj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

        FileResponse response = fileService.upload(pdf("report.pdf", content), alice);

        assertNotNull(response);
        assertEquals("report.pdf", response.getOriginalFilename());
        assertEquals("application/pdf", response.getMimeType());
        assertEquals(content.length, response.getSize());

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).save(captor.capture());
        FileRecord record = captor.getValue();
        assertEquals(alice, record.getUser());
        assertEquals("pdf", record.getExtension());
        assertEquals(64, record.getSha256().length());
    }

    @Test
    @DisplayName("正常 DOCX 上传：OOXML 结构特征通过")
    void shouldUploadValidDocx() throws Exception {
        byte[] content = minimalDocx();
        MockMultipartFile docx = new MockMultipartFile("file", "doc.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);

        FileResponse response = fileService.upload(docx, alice);

        assertEquals("doc.docx", response.getOriginalFilename());
        verify(fileRepository).save(any(FileRecord.class));
    }

    @Test
    @DisplayName("UTF-8 中文 txt 上传通过（有符号 byte 回归：非 ASCII 字节不得误判为控制字符）")
    void shouldUploadChineseUtf8Txt() {
        byte[] content = "这是一段中文笔记内容".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile txt = new MockMultipartFile("file", "note.txt", "text/plain", content);

        FileResponse response = fileService.upload(txt, alice);

        assertEquals("note.txt", response.getOriginalFilename());
        verify(fileRepository).save(any(FileRecord.class));
    }

    // ==================== 拒绝场景 ====================

    @Test
    @DisplayName("空文件拒绝 → InvalidFileException")
    void shouldRejectEmptyFile() {
        MockMultipartFile empty = pdf("empty.pdf", new byte[0]);
        assertThrows(InvalidFileException.class, () -> fileService.upload(empty, alice));
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("超大文件拒绝 → FileTooLargeException")
    void shouldRejectOversizedFile() {
        when(storageProperties.getMaxFileSize()).thenReturn(DataSize.ofBytes(10));
        byte[] big = "%PDF-1.4\n123456789\n".getBytes(StandardCharsets.US_ASCII);

        assertThrows(FileTooLargeException.class, () -> fileService.upload(pdf("big.pdf", big), alice));
    }

    @Test
    @DisplayName("非 allowlist 扩展名拒绝 → InvalidFileException")
    void shouldRejectDisallowedExtension() {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe",
                "application/octet-stream", "MZ...".getBytes());
        assertThrows(InvalidFileException.class, () -> fileService.upload(exe, alice));
    }

    @Test
    @DisplayName("扩展名与真实类型不一致拒绝：.pdf 文件名 + 纯文本内容")
    void shouldRejectPdfWithTextContent() {
        byte[] text = "hello world, I am not a pdf".getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidFileException.class, () -> fileService.upload(pdf("fake.pdf", text), alice));
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("声明 MIME 与扩展名不一致拒绝")
    void shouldRejectMismatchedDeclaredMime() {
        byte[] content = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile wrongMime = new MockMultipartFile("file", "report.pdf", "text/plain", content);
        assertThrows(InvalidFileException.class, () -> fileService.upload(wrongMime, alice));
    }

    // ==================== SHA-256 / storageKey ====================

    @Test
    @DisplayName("SHA-256 正确生成（流式计算）")
    void shouldComputeCorrectSha256() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile txt = new MockMultipartFile("file", "hello.txt", "text/plain", content);

        fileService.upload(txt, alice);

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).save(captor.capture());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                captor.getValue().getSha256());
    }

    @Test
    @DisplayName("storageKey 由系统生成，不使用原始文件名")
    void shouldNotUseOriginalFilenameInStorageKey() {
        byte[] content = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        fileService.upload(pdf("report.pdf", content), alice);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(keyCaptor.capture(), any(), anyLong());
        String key = keyCaptor.getValue();
        assertFalse(key.contains("report"), "storageKey 不得包含原始文件名");
        assertTrue(key.matches("1/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf"), "格式应为 userId/yyyy/MM/uuid.ext: " + key);
    }

    // ==================== 一致性补偿 ====================

    @Test
    @DisplayName("数据库保存失败 → 补偿删除已写入的物理文件")
    void shouldDeletePhysicalFileWhenDbSaveFails() {
        when(fileRepository.save(any(FileRecord.class)))
                .thenThrow(new RuntimeException("数据库写入失败"));
        byte[] content = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        assertThrows(RuntimeException.class, () -> fileService.upload(pdf("report.pdf", content), alice));
        verify(storageService).delete(anyString());
    }

    // ==================== 归属校验 ====================

    @Test
    @DisplayName("列表只返回当前用户的文件")
    void shouldListOnlyOwnFiles() {
        when(fileRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        assertEquals(0, fileService.listFiles(alice).size());
        verify(fileRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("访问他人文件 → ForbiddenException")
    void shouldRejectOtherUsersFile() {
        User bob = new User(2L, "bob", "pwd");
        FileRecord others = new FileRecord(bob, "secret.pdf", "2/2026/08/x.pdf", "pdf",
                "application/pdf", 10, "abc");
        others.setId(5L);
        when(fileRepository.findByIdWithUser(5L)).thenReturn(Optional.of(others));

        assertThrows(ForbiddenException.class, () -> fileService.getFile(5L, alice));
        assertThrows(ForbiddenException.class, () -> fileService.downloadFile(5L, alice));
        assertThrows(ForbiddenException.class, () -> fileService.deleteFile(5L, alice));
        verify(storageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("访问不存在的文件 → FileRecordNotFoundException")
    void shouldThrowNotFoundForMissingFile() {
        when(fileRepository.findByIdWithUser(99L)).thenReturn(Optional.empty());
        assertThrows(FileRecordNotFoundException.class, () -> fileService.getFile(99L, alice));
    }

    // ==================== 下载 / 删除 ====================

    @Test
    @DisplayName("下载自己的文件返回 FileDownload")
    void shouldDownloadOwnFile() {
        FileRecord own = new FileRecord(alice, "note.txt", "1/2026/08/n.txt", "txt",
                "text/plain", 5, "abc");
        own.setId(1L);
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(own));
        when(storageService.load("1/2026/08/n.txt"))
                .thenReturn(new ByteArrayResource("hello".getBytes()));

        var download = fileService.downloadFile(1L, alice);

        assertEquals("note.txt", download.filename());
        assertEquals("text/plain", download.mimeType());
        assertNotNull(download.resource());
    }

    @Test
    @DisplayName("删除自己的文件：先删记录再删物理文件")
    void shouldDeleteOwnFile() {
        FileRecord own = new FileRecord(alice, "note.txt", "1/2026/08/n.txt", "txt",
                "text/plain", 5, "abc");
        own.setId(1L);
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(own));

        fileService.deleteFile(1L, alice);

        verify(fileRepository).delete(own);
        verify(storageService).delete("1/2026/08/n.txt");
    }
}
