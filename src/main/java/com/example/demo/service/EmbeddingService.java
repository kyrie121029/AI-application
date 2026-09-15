package com.example.demo.service;

import com.example.demo.dto.BatchEmbeddingResult;
import com.example.demo.dto.EmbeddingResult;

import java.util.List;

/**
 * Embedding 服务抽象 —— 文本 → 向量（独立于文本生成 AIService）。
 * <p>
 * 只负责「文本 → 向量」，不感知 DocumentChunk / FileRecord / Repository / Vector DB。
 */
public interface EmbeddingService {

    /** 单文本 Embedding（内部复用 embedBatch） */
    EmbeddingResult embed(String text);

    /** 批量 Embedding；返回的 embeddings 按输入顺序排列 */
    BatchEmbeddingResult embedBatch(List<String> texts);

    /** 当前 Provider 标识（用于日志） */
    String getProvider();

    /** 当前模型名（用于日志） */
    String getModelName();

    /** 期望向量维度 */
    int getDimension();
}
