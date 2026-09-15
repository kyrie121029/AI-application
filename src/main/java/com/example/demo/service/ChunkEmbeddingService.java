package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.dto.BatchEmbeddingResult;
import com.example.demo.dto.ChunkEmbeddingDraft;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.EmbeddingCallException;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.model.AIUsageLog;
import com.example.demo.model.DocumentChunk;
import com.example.demo.model.FileRecord;
import com.example.demo.repository.AIUsageLogRepository;
import com.example.demo.repository.DocumentChunkRepository;
import com.example.demo.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk Embedding 业务编排 —— fileId → Chunk → 分批 → Embedding → 映射回 chunkId/chunkIndex。
 * <p>
 * 职责边界：EmbeddingService 不认识 DocumentChunk；本层负责分批、映射与 AIUsageLog 记录。
 * Phase 7.2 不持久化 Vector（Vector Store 数据模型留 Phase 7.3）。
 */
@Service
public class ChunkEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkEmbeddingService.class);

    private final DocumentChunkRepository chunkRepository;
    private final FileRepository fileRepository;
    private final EmbeddingService embeddingService;
    private final EmbeddingProperties embeddingProperties;
    private final AIUsageLogRepository usageLogRepository;

    public ChunkEmbeddingService(DocumentChunkRepository chunkRepository,
                                 FileRepository fileRepository,
                                 EmbeddingService embeddingService,
                                 EmbeddingProperties embeddingProperties,
                                 AIUsageLogRepository usageLogRepository) {
        this.chunkRepository = chunkRepository;
        this.fileRepository = fileRepository;
        this.embeddingService = embeddingService;
        this.embeddingProperties = embeddingProperties;
        this.usageLogRepository = usageLogRepository;
    }

    /** 对一个文件的所有 Chunk 做 Embedding，返回按 chunkIndex 排序的 draft 列表 */
    public List<ChunkEmbeddingDraft> embedFile(Long fileId) {
        FileRecord record = fileRepository.findByIdWithUser(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
        Long userId = record.getUser().getId();

        List<DocumentChunk> chunks = chunkRepository.findByFileIdOrderByChunkIndexAsc(fileId);
        List<ChunkEmbeddingDraft> drafts = new ArrayList<>();

        List<List<DocumentChunk>> batches = partition(chunks, embeddingProperties.getBatchSize());
        for (List<DocumentChunk> batch : batches) {
            List<String> texts = batch.stream().map(DocumentChunk::getContent).toList();
            long start = System.currentTimeMillis();
            try {
                BatchEmbeddingResult result = embeddingService.embedBatch(texts);
                // 映射：result.embeddings 已按 index 恢复顺序，get(i) 对应 batch 第 i 个 chunk
                for (int i = 0; i < batch.size(); i++) {
                    DocumentChunk chunk = batch.get(i);
                    float[] vector = result.embeddings().get(i).vector();
                    drafts.add(new ChunkEmbeddingDraft(chunk.getId(), chunk.getChunkIndex(),
                            vector, result.model(), result.dimension()));
                }
                saveUsageLog(userId, fileId, result, System.currentTimeMillis() - start,
                        true, null, result.retryCount());
            } catch (AIServiceException e) {
                int retryCount = (e instanceof EmbeddingCallException ec) ? ec.getRetryCount() : 0;
                saveUsageLog(userId, fileId, null, System.currentTimeMillis() - start,
                        false, e.getErrorType(), retryCount);
                throw e;
            }
        }
        log.info("Chunk Embedding 完成: fileId={}, chunks={}, batches={}",
                fileId, chunks.size(), batches.size());
        return drafts;
    }

    /** 当前 Embedding 模型（用于索引元数据） */
    public String currentModel() {
        return embeddingService.getModelName();
    }

    /** 当前 Embedding 维度（用于索引元数据） */
    public int currentDimension() {
        return embeddingService.getDimension();
    }

    /** 记录一条 AIUsageLog（一个 batch 一次真实 Provider 调用 → 一条日志） */
    private void saveUsageLog(Long userId, Long fileId, BatchEmbeddingResult result,
                              long elapsedMs, boolean success, String errorType, int retryCount) {
        try {
            AIUsageLog entry = new AIUsageLog();
            entry.setUserId(userId);
            entry.setFileId(fileId);
            entry.setCallType("EMBEDDING");
            entry.setProvider(result != null ? result.provider() : embeddingService.getProvider());
            entry.setModelName(result != null ? result.model() : embeddingService.getModelName());
            // outputTokens 不适用 Embedding，保持 0（非"生成 0 token"含义）
            entry.setInputTokens(result != null && result.inputTokens() != null ? result.inputTokens() : 0);
            entry.setElapsedMs(elapsedMs);
            entry.setSuccess(success);
            entry.setErrorType(errorType);
            entry.setRetryCount(retryCount);
            // promptVersion / requestPreview 不适用 Embedding，不设置
            usageLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("记录 Embedding 调用日志失败: fileId={}", fileId, e);
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return result;
    }
}
