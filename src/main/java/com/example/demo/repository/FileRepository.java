package com.example.demo.repository;

import com.example.demo.model.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文件元数据数据访问层
 */
@Repository
public interface FileRepository extends JpaRepository<FileRecord, Long> {

    /** 当前用户的文件列表（时间倒序） */
    List<FileRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** JOIN FETCH user —— 归属校验时访问 record.getUser() 不依赖事务，避免 LazyInitializationException */
    @Query("select f from FileRecord f join fetch f.user where f.id = :id")
    Optional<FileRecord> findByIdWithUser(@Param("id") Long id);
}
