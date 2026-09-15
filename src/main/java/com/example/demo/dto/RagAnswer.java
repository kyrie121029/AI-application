package com.example.demo.dto;

import java.util.List;

/**
 * RAG 生成答案 + 经过校验的 Citation。
 */
public record RagAnswer(
        String answer,
        List<RagCitation> citations,
        String model
) {
}
