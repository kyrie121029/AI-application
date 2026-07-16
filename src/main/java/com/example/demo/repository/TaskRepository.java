package com.example.demo.repository;

import com.example.demo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 任务数据访问层 —— 继承 JpaRepository 就等于拥有了增删查改能力
 * <p>
 * 只需声明接口，不用写实现类。Spring Data JPA 会在运行时自动生成实现。
 * <p>
 * 常用方法（免费获得）：
 *   save(Task)      → 新增 / 更新
 *   findById(Long)  → 按 ID 查
 *   findAll()       → 查全部
 *   deleteById(Long)→ 按 ID 删
 *   count()         → 统计数量
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    // JpaRepository<Task, Long>：操作 Task 表，主键类型是 Long
    // 这里不需要写任何方法，基础的增删查改全都有
}