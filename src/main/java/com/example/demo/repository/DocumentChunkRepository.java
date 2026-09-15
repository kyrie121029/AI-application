package com.example.demo.repository;

import com.example.demo.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RAG Chunk 数据访问层
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByFileIdOrderByChunkIndexAsc(Long fileId);

    /**
     * 检索 owner 二次校验：按 chunkId + 当前 userId 取 Chunk。
     * join fetch file，使 RetrievalService 可用 chunk.getFile().getId() 作为权威 fileId。
     */
    @Query("select c from DocumentChunk c join fetch c.file where c.id = :chunkId and c.file.user.id = :userId")
    Optional<DocumentChunk> findOwnedChunk(@Param("chunkId") Long chunkId,
                                           @Param("userId") Long userId);

    /**
     * 重新生成前删除旧 Chunk（级联删除 source）。
     * 用 bulk delete 而非派生 deleteByFileId：确保 DELETE 在 INSERT 前执行，
     * 避免同一事务内新 chunk 与旧 chunk 撞 (file_id, chunk_index) 唯一约束。
     */
    @Modifying
    @Query("delete from DocumentChunk c where c.file.id = :fileId")
    void deleteByFileId(@Param("fileId") Long fileId);
}
