package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.ChunkDraft;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.model.DocumentChunk;
import com.example.demo.model.DocumentChunkSource;
import com.example.demo.model.DocumentSegment;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.DocumentChunkRepository;
import com.example.demo.repository.DocumentChunkSourceRepository;
import com.example.demo.repository.DocumentSegmentRepository;
import com.example.demo.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分块业务编排 —— 读取 Segment → 调用策略 → 幂等替换旧 Chunk。
 * <p>
 * rechunk / chunkFile 均为 public @Transactional（经代理生效），核心逻辑在 doChunk（private）。
 * 幂等：事务内删除旧 Chunk（级联删 source）→ 保存新 Chunk + source；
 * 唯一约束 (file_id, chunk_index) 兜底；整段替换保证失败时回滚，不留半更新状态。
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    private final DocumentSegmentRepository segmentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentChunkSourceRepository sourceRepository;
    private final FileRepository fileRepository;
    private final ChunkingStrategy chunkingStrategy;
    private final RagProperties ragProperties;

    public ChunkingService(DocumentSegmentRepository segmentRepository,
                           DocumentChunkRepository chunkRepository,
                           DocumentChunkSourceRepository sourceRepository,
                           FileRepository fileRepository,
                           ChunkingStrategy chunkingStrategy,
                           RagProperties ragProperties) {
        this.segmentRepository = segmentRepository;
        this.chunkRepository = chunkRepository;
        this.sourceRepository = sourceRepository;
        this.fileRepository = fileRepository;
        this.chunkingStrategy = chunkingStrategy;
        this.ragProperties = ragProperties;
    }

    /** 手动重建入口：归属校验 + 分块 */
    @Transactional
    public void rechunk(Long fileId, User user) {
        doChunk(requireOwned(fileId, user));
    }

    /** 内部触发（解析成功后）：不校验归属（调用方已确认 fileId 合法） */
    @Transactional
    public void chunkFile(Long fileId) {
        doChunk(require(fileId));
    }

    /** 核心：读取排序后的 Segment → 计算 → 删旧存新（在调用者事务内） */
    private void doChunk(FileRecord record) {
        List<DocumentSegment> segments =
                segmentRepository.findByFileIdOrderBySegmentIndexAsc(record.getId());
        List<ChunkDraft> drafts = chunkingStrategy.chunk(
                segments, ragProperties.getChunk().getMaxChars(), ragProperties.getChunk().getOverlapChars());

        chunkRepository.deleteByFileId(record.getId());
        int index = 0;
        for (ChunkDraft draft : drafts) {
            DocumentChunk chunk = chunkRepository.save(
                    new DocumentChunk(record, index, draft.content(), draft.content().length()));
            int position = 0;
            for (ChunkDraft.SourceRef ref : draft.sources()) {
                sourceRepository.save(new DocumentChunkSource(
                        chunk, ref.segmentId(), ref.type(), ref.segmentIndex(),
                        ref.startOffset(), ref.endOffset(), position++));
            }
            index++;
        }
        log.info("文档分块完成: fileId={}, chunks={}", record.getId(), drafts.size());
    }

    private FileRecord requireOwned(Long fileId, User user) {
        FileRecord record = require(fileId);
        if (!record.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("无权访问该文件");
        }
        return record;
    }

    private FileRecord require(Long fileId) {
        return fileRepository.findByIdWithUser(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
    }
}
