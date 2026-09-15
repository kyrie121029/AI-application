package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.ChunkEmbeddingDraft;
import com.example.demo.dto.VectorRecord;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档索引编排 —— fileId → Embedding → VectorStore.upsert。
 * <p>
 * 完整调用链：
 *   fileId
 *   → ChunkEmbeddingService.embedFile()   （只产 ChunkEmbeddingDraft，不依赖 Qdrant）
 *   → 组装 VectorRecord
 *   → VectorStore.upsert()
 *   → DocumentIndexPersistenceService 短事务更新状态
 * <p>
 * 幂等重建：upsert 用稳定 point id 覆盖；每次重建前 deleteByFileId 清旧点。
 * 失败一致性：embedding/upsert 任一步失败 → DocumentIndex FAILED，可重试；
 * Qdrant 是从 MySQL Chunk 可重建的派生索引，delete-then-upsert 半完成态可整体重建恢复。
 */
@Service
public class DocumentIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

    private final FileRepository fileRepository;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final DocumentIndexPersistenceService persistence;

    public DocumentIndexService(FileRepository fileRepository,
                                ChunkEmbeddingService chunkEmbeddingService,
                                VectorStore vectorStore,
                                RagProperties ragProperties,
                                DocumentIndexPersistenceService persistence) {
        this.fileRepository = fileRepository;
        this.chunkEmbeddingService = chunkEmbeddingService;
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.persistence = persistence;
    }

    /** 对一个文件建立/重建向量索引（归属校验） */
    public void indexFile(Long fileId, User user) {
        FileRecord record = requireOwned(fileId, user);
        Long userId = record.getUser().getId();

        // 短事务 + 原子 CAS：仅成功抢占 INDEXING 的请求继续；并发请求抛冲突
        persistence.beginIndex(fileId,
                chunkEmbeddingService.currentModel(), chunkEmbeddingService.currentDimension(),
                ragProperties.getIndex().getVersion());

        try {
            // 顺序：先全部 Embedding（失败不触碰旧索引）→ 清旧点 → upsert 新点
            List<ChunkEmbeddingDraft> drafts = chunkEmbeddingService.embedFile(fileId);
            List<VectorRecord> records = new ArrayList<>(drafts.size());
            for (ChunkEmbeddingDraft d : drafts) {
                records.add(new VectorRecord(fileId, userId, d.chunkId(), d.chunkIndex(),
                        d.vector(), d.model(), d.dimension(), ragProperties.getIndex().getVersion()));
            }

            // Embedding 成功后 delete → upsert（delete 成功但 upsert 失败的空窗是已知技术债，可 reindex 恢复）
            vectorStore.deleteByFileId(fileId);
            if (!records.isEmpty()) {
                vectorStore.upsert(records);
            }

            persistence.markSuccess(fileId);
            log.info("文档索引成功: fileId={}, points={}", fileId, records.size());
        } catch (Exception e) {
            log.error("文档索引失败: fileId={}", fileId, e);
            persistence.markFailed(fileId, safeReason(e));
            throw e;
        }
    }

    /** 删除文件时清理派生向量（供 FileService.deleteFile 调用，Qdrant 不可达不影响删除） */
    public void deleteVectorIndex(Long fileId) {
        try {
            vectorStore.deleteByFileId(fileId);
        } catch (Exception e) {
            log.warn("删除派生向量失败（可后续清理）: fileId={}", fileId, e);
        }
    }

    private FileRecord requireOwned(Long fileId, User user) {
        FileRecord record = fileRepository.findByIdWithUser(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
        if (!record.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("无权访问该文件");
        }
        return record;
    }

    private String safeReason(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) return e.getClass().getSimpleName();
        return m.length() > 400 ? m.substring(0, 400) : m;
    }
}
