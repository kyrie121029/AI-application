package com.example.demo.dto;

import java.util.List;

/**
 * Context Assembly 结果。
 * <p>
 * context 是带 [S1]/[S2] 标记的检索原文；sources 按 rank 顺序记录实际使用的 Chunk，
 * estimatedTokens 为估算（非精确）；truncated 表示是否因预算被截断。
 */
public record ContextAssemblyResult(
        String context,
        List<RagContextSource> sources,
        int estimatedTokens,
        boolean truncated
) {
}
