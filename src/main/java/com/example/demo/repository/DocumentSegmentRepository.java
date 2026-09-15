package com.example.demo.repository;

import com.example.demo.model.DocumentSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档解析片段数据访问层
 */
@Repository
public interface DocumentSegmentRepository extends JpaRepository<DocumentSegment, Long> {

    /** 按文件查询解析片段（按来源序号升序，保持文档顺序） */
    List<DocumentSegment> findByFileIdOrderBySegmentIndexAsc(Long fileId);

    /** 删除某文件的全部片段（重试解析前清理旧结果） */
    void deleteByFileId(Long fileId);
}
