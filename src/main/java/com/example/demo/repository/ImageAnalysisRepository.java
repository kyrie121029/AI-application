package com.example.demo.repository;

import com.example.demo.model.ImageAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 图片分析数据访问层 —— 状态转换用原子条件 UPDATE，避免 check-then-act 并发窗口。
 */
@Repository
public interface ImageAnalysisRepository extends JpaRepository<ImageAnalysis, Long> {

    Optional<ImageAnalysis> findByFileId(Long fileId);

    /** 仅 FAILED → PENDING（可重试）；返回影响行数，0 表示状态冲突 */
    @Modifying
    @Query("update ImageAnalysis a set a.status = com.example.demo.enums.ImageAnalysisStatus.PENDING, " +
            "a.failureReason = null where a.file.id = :fileId " +
            "and a.status = com.example.demo.enums.ImageAnalysisStatus.FAILED")
    int resetToPending(@Param("fileId") Long fileId);

    /** 仅 PENDING → PROCESSING；返回影响行数，0 表示状态已被抢占 */
    @Modifying
    @Query("update ImageAnalysis a set a.status = com.example.demo.enums.ImageAnalysisStatus.PROCESSING " +
            "where a.file.id = :fileId " +
            "and a.status = com.example.demo.enums.ImageAnalysisStatus.PENDING")
    int markProcessing(@Param("fileId") Long fileId);
}
