package com.example.demo.repository;

import com.example.demo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 任务数据访问层
 * <p>
 * JpaRepository<Task, Long>         → 基础 CRUD
 * JpaSpecificationExecutor<Task>    → 动态条件查询（按状态/类型/关键字筛选）
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    // 所有方法由 Spring Data JPA 自动生成
}
