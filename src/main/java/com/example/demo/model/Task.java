package com.example.demo.model;

import com.example.demo.enums.TaskStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 任务模型 —— JPA 会自动把这个类映射为数据库中的一张表
 * <p>
 * 加了 @Entity 之后，Task 不再只是内存中的 Java 对象，而是数据库的一条记录。
 */
@Entity
@Table(name = "tasks")   // 指定表名，不写则默认用类名小写
public class Task {

    /** 主键，数据库自动生成 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 标题，不能为空 */
    @Column(nullable = false)
    private String title;

    /** 任务类型，如 "文本分析"、"情感识别" 等 */
    private String taskType;

    /** 用户输入的待分析文本，用 @Lob 标记为大文本 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String inputText;

    /** 当前任务状态 */
    @Enumerated(EnumType.STRING)  // 枚举存为字符串而非数字
    private TaskStatus status;

    /** AI 分析结果 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String result;

    /** 任务创建时间 */
    private LocalDateTime createdAt;

    /** 任务最后更新时间 */
    private LocalDateTime updatedAt;

    // ==================== 构造方法 ====================

    public Task() {
    }

    public Task(Long id, String title, String taskType, String inputText) {
        this.id = id;
        this.title = title;
        this.taskType = taskType;
        this.inputText = inputText;
        this.status = TaskStatus.PENDING;
        this.result = null;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}