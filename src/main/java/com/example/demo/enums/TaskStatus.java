package com.example.demo.enums;

/**
 * 任务状态枚举 —— 定义任务在整个生命周期中的可能状态
 */
public enum TaskStatus {

    /** 待处理：任务已创建，尚未生成分析结果 */
    PENDING,

    /** 结果已生成：分析结果已生成完毕 */
    RESULT_GENERATED
}
