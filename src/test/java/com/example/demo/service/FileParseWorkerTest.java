package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.enums.SegmentType;
import com.example.demo.exception.DocumentParseException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileParseWorker 单元测试 —— mock Storage / Parser / Persistence，
 * 直接调用 runAsync（不触发 @Async，同步执行）验证状态机与失败路径。
 */
@ExtendWith(MockitoExtension.class)
class FileParseWorkerTest {

    @Mock private FileRepository fileRepository;
    @Mock private StorageService storageService;
    @Mock private DocumentParserRegistry parserRegistry;
    @Mock private FileParsePersistenceService persistence;
    @Mock private ChunkingService chunkingService;
    @InjectMocks private FileParseWorker worker;

    private FileRecord record() {
        FileRecord r = new FileRecord(new User(1L, "alice", "pwd"), "a.pdf",
                "1/2026/08/x.pdf", "pdf", "application/pdf", 10, "hash");
        r.setId(1L);
        return r;
    }

    private void stubLoadable() {
        when(fileRepository.findById(1L)).thenReturn(Optional.of(record()));
        Resource resource = new InputStreamResource(
                new ByteArrayInputStream("%PDF".getBytes(StandardCharsets.US_ASCII)));
        when(storageService.load("1/2026/08/x.pdf")).thenReturn(resource);
    }

    @Test
    @DisplayName("解析成功：PROCESSING → SUCCESS，保存片段")
    void successFlow() {
        stubLoadable();
        DocumentParser parser = mock(DocumentParser.class);
        when(parserRegistry.get("pdf")).thenReturn(parser);
        when(parser.parse(any())).thenReturn(new ParsedDocument(List.of(
                new ParsedDocument.ParsedSegment(SegmentType.PAGE, 1, "content"))));

        worker.runAsync(1L);

        InOrder inOrder = inOrder(persistence);
        inOrder.verify(persistence).markProcessing(1L);
        inOrder.verify(persistence).markSuccess(eq(1L), any());
        verify(persistence, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("Parser 失败：PROCESSING → FAILED，保存失败原因")
    void parserFailureFlowsToFailed() {
        stubLoadable();
        DocumentParser parser = mock(DocumentParser.class);
        when(parserRegistry.get("pdf")).thenReturn(parser);
        when(parser.parse(any())).thenThrow(new DocumentParseException("解析失败原因"));

        worker.runAsync(1L);

        InOrder inOrder = inOrder(persistence);
        inOrder.verify(persistence).markProcessing(1L);
        inOrder.verify(persistence).markFailed(eq(1L), eq("解析失败原因"));
        verify(persistence, never()).markSuccess(any(), any());
    }

    @Test
    @DisplayName("StorageService.load 失败：PROCESSING → FAILED")
    void loadFailureFlowsToFailed() {
        when(fileRepository.findById(1L)).thenReturn(Optional.of(record()));
        when(storageService.load("1/2026/08/x.pdf"))
                .thenThrow(new com.example.demo.exception.FileStorageException("读取失败"));

        worker.runAsync(1L);

        verify(persistence).markProcessing(1L);
        verify(persistence).markFailed(eq(1L), contains("读取失败"));
        verify(persistence, never()).markSuccess(any(), any());
    }
}
