package com.example.demo.repository;

import com.example.demo.model.DocumentChunkSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Chunk 来源映射数据访问层
 */
@Repository
public interface DocumentChunkSourceRepository extends JpaRepository<DocumentChunkSource, Long> {

    /** 按 chunk 查询来源（顺序） */
    List<DocumentChunkSource> findByChunkIdOrderByPositionAsc(Long chunkId);
}
