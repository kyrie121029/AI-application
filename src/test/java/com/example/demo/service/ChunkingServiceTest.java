package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.ChunkDraft;
import com.example.demo.enums.SegmentType;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.DocumentChunkRepository;
import com.example.demo.repository.DocumentChunkSourceRepository;
import com.example.demo.repository.DocumentSegmentRepository;
import com.example.demo.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChunkingService 编排测试 —— 幂等替换、归属校验、Chunk + Source 保存。
 */
@ExtendWith(MockitoExtension.class)
class ChunkingServiceTest {

    @Mock private DocumentSegmentRepository segmentRepository;
    @Mock private DocumentChunkRepository chunkRepository;
    @Mock private DocumentChunkSourceRepository sourceRepository;
    @Mock private FileRepository fileRepository;
    @Mock private ChunkingStrategy strategy;

    private RagProperties ragProperties;
    private ChunkingService service;

    private final User alice = new User(1L, "alice", "pwd");
    private final User bob = new User(2L, "bob", "pwd");

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getChunk().setMaxChars(100);
        ragProperties.getChunk().setOverlapChars(10);
        service = new ChunkingService(segmentRepository, chunkRepository, sourceRepository,
                fileRepository, strategy, ragProperties);
    }

    private FileRecord record(User owner) {
        FileRecord r = new FileRecord(owner, "a.txt", "1/2026/08/a.txt", "txt",
                "text/plain", 10, "hash");
        r.setId(1L);
        return r;
    }

    private ChunkDraft draft(String content) {
        return new ChunkDraft(content, List.of(
                new ChunkDraft.SourceRef(1L, SegmentType.LINE, 1, 0, content.length())));
    }

    @Test
    @DisplayName("分块保存：删旧 + 保存新 Chunk 与 Source")
    void savesChunksAndSources() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record(alice)));
        when(segmentRepository.findByFileIdOrderBySegmentIndexAsc(1L)).thenReturn(List.of());
        when(strategy.chunk(any(), eq(100), eq(10))).thenReturn(List.of(draft("AAA"), draft("BBB")));

        service.rechunk(1L, alice);

        verify(chunkRepository).deleteByFileId(1L);
        verify(chunkRepository, times(2)).save(any());
        verify(sourceRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("幂等：重复执行都先删旧 Chunk（不追加重复数据）")
    void repeatedExecutionDeletesOldChunks() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record(alice)));
        when(segmentRepository.findByFileIdOrderBySegmentIndexAsc(1L)).thenReturn(List.of());
        when(strategy.chunk(any(), anyInt(), anyInt())).thenReturn(List.of(draft("AAA")));

        service.rechunk(1L, alice);
        service.rechunk(1L, alice);

        verify(chunkRepository, times(2)).deleteByFileId(1L);
        verify(chunkRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("他人文件 → ForbiddenException")
    void otherUserForbidden() {
        when(fileRepository.findByIdWithUser(1L)).thenReturn(Optional.of(record(bob)));

        assertThrows(ForbiddenException.class, () -> service.rechunk(1L, alice));
        verify(chunkRepository, never()).deleteByFileId(any());
    }

    @Test
    @DisplayName("文件不存在 → FileRecordNotFoundException")
    void missingFileThrows() {
        when(fileRepository.findByIdWithUser(99L)).thenReturn(Optional.empty());

        assertThrows(FileRecordNotFoundException.class, () -> service.rechunk(99L, alice));
    }
}
