package com.example.demo.tool;

import com.example.demo.dto.RetrievalResult;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.model.User;
import com.example.demo.service.RetrievalService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool：searchKnowledgeBase —— 检索当前用户知识库（只读）。
 * 权限：RetrievalService 已在检索 filter 按 userId 过滤。
 */
@Component
public class SearchKnowledgeBaseTool implements Tool {

    private static final int CONTENT_PREVIEW = 200;

    private final RetrievalService retrievalService;

    public SearchKnowledgeBaseTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("searchKnowledgeBase",
                        "在用户自己的文档知识库中做向量检索，返回命中的文档片段与分数")
                .param("query", ToolDefinition.STRING, "检索问题（必填）", true)
                .param("fileIds", ToolDefinition.ARRAY_INTEGER, "限定检索的文件 id 列表（可空）", false)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, User user) {
        String query = ToolArgs.toString(arguments.get("query"));
        List<Long> fileIds = ToolArgs.toLongList(arguments.get("fileIds"));
        try {
            List<RetrievalResult> results = retrievalService.search(query, user, fileIds);
            List<Map<String, Object>> trimmed = new java.util.ArrayList<>();
            for (RetrievalResult r : results) {
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("chunkId", r.chunkId());
                row.put("fileId", r.fileId());
                row.put("chunkIndex", r.chunkIndex());
                row.put("score", r.score());
                row.put("content", preview(r.content()));
                trimmed.add(row);
            }
            return ToolResult.ok("searchKnowledgeBase", trimmed);
        } catch (InvalidFileException e) {
            return ToolResult.error("searchKnowledgeBase", ToolErrorCode.INVALID_ARGUMENT,
                    e.getMessage(), false);
        }
    }

    private String preview(String content) {
        if (content == null) return "";
        return content.length() > CONTENT_PREVIEW ? content.substring(0, CONTENT_PREVIEW) + "..." : content;
    }
}
