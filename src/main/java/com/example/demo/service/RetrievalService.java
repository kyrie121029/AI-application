package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.dto.EmbeddingResult;
import com.example.demo.dto.RetrievalResult;
import com.example.demo.dto.VectorSearchRequest;
import com.example.demo.dto.VectorSearchResult;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.model.DocumentChunk;
import com.example.demo.model.User;
import com.example.demo.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础 Dense Vector 检索编排（不含 LLM / Context Assembly）。
 * <p>
 * query
 *   → EmbeddingService.embed          （Query Vector）
 *   → VectorStore.search              （Qdrant filter 阶段按 userId+indexVersion 过滤）
 *   → DocumentChunkRepository.findById （回 MySQL 取原文）
 *   → RetrievalResult（保持 Qdrant score/rank 顺序；orphan chunk 跳过并 warn）
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final DocumentChunkRepository chunkRepository;
    private final RagProperties ragProperties;

    public RetrievalService(EmbeddingService embeddingService,
                            VectorStore vectorStore,
                            DocumentChunkRepository chunkRepository,
                            RagProperties ragProperties) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.ragProperties = ragProperties;
    }

    /** 检索当前用户全部可检索文件 */
    public List<RetrievalResult> search(String query, User user) {
        return search(query, user, null);
    }

    /** 检索限定在指定文件（fileIds）内；空 query 拒绝 */
    public List<RetrievalResult> search(String query, User user, List<Long> fileIds) {
        if (query == null || query.isBlank()) {
            throw new InvalidFileException("检索 query 不能为空");
        }

        EmbeddingResult embedding = embeddingService.embed(query);
        VectorSearchRequest request = new VectorSearchRequest(
                embedding.vector(),
                user.getId(),
                fileIds,
                ragProperties.getRetrieval().getTopK(),
                ragProperties.getRetrieval().getScoreThreshold(),
                ragProperties.getIndex().getVersion());

        List<VectorSearchResult> hits = vectorStore.search(request);

        List<RetrievalResult> results = new ArrayList<>();
        for (VectorSearchResult hit : hits) {
            // Qdrant filter 之外再按 chunkId + 当前 userId 回 MySQL 二次校验：
            // Qdrant 命中但 MySQL 无此 chunk，或该 chunk 不属于当前用户 → 跳过并告警
            DocumentChunk chunk = chunkRepository.findOwnedChunk(hit.chunkId(), user.getId()).orElse(null);
            if (chunk == null) {
                log.warn("检索命中不可用/越权 chunk，跳过: chunkId={}, qdrantFileId={}, userId={}",
                        hit.chunkId(), hit.fileId(), user.getId());
                continue;
            }
            // 业务事实字段以 MySQL DocumentChunk/FileRecord 为准；score 保留 Qdrant 值
            results.add(new RetrievalResult(chunk.getFile().getId(), chunk.getId(),
                    chunk.getChunkIndex(), chunk.getContent(), hit.score()));
        }
        return results;
    }
}
