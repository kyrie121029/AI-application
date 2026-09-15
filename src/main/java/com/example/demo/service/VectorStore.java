package com.example.demo.service;

import com.example.demo.dto.VectorRecord;
import com.example.demo.dto.VectorSearchRequest;
import com.example.demo.dto.VectorSearchResult;

import java.util.List;

/**
 * 向量存储抽象 —— 业务层只依赖此接口，不直接接触 Qdrant 等具体实现。
 * <p>
 * Qdrant 是从 MySQL DocumentChunk 可重建的派生索引，非业务事实源。
 */
public interface VectorStore {

    /** 批量写入/覆盖向量（幂等：稳定 point id + upsert） */
    void upsert(List<VectorRecord> records);

    /** 删除某文件的所有向量（按 payload fileId 过滤） */
    void deleteByFileId(Long fileId);

    /** 相似度检索（按 score 降序返回） */
    List<VectorSearchResult> search(VectorSearchRequest request);
}