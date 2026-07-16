package com.example.demo.exception;

/**
 * 任务不存在异常 —— 查/删/操作不存在的 ID 时抛出
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("任务不存在: id=" + id);
    }
}