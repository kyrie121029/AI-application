package com.example.demo.repository;

import com.example.demo.enums.IndexStatus;
import com.example.demo.model.DocumentIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档索引状态数据访问层
 */
@Repository
public interface DocumentIndexRepository extends JpaRepository<DocumentIndex, Long> {

    Optional<DocumentIndex> findByFileId(Long fileId);

    /**
     * 原子 CAS：仅从非 INDEXING 状态切到 INDEXING，返回影响行数。
     * 返回 0 = 另一请求正 INDEXING（并发拒绝）。
     */
    @Modifying
    @Query("update DocumentIndex d set d.status = com.example.demo.enums.IndexStatus.INDEXING, " +
            "d.embeddingModel = :model, d.dimension = :dimension, " +
            "d.indexVersion = :indexVersion, d.errorMessage = null, d.updatedAt = current_timestamp " +
            "where d.file.id = :fileId and d.status <> com.example.demo.enums.IndexStatus.INDEXING")
    int tryMarkIndexing(@Param("fileId") Long fileId,
                        @Param("model") String model,
                        @Param("dimension") int dimension,
                        @Param("indexVersion") String indexVersion);
}

