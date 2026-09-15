package com.example.demo.tool;

import com.example.demo.dto.FileResponse;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.model.User;
import com.example.demo.service.FileService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool：getFileInfo —— 查询文件元数据（只读，不含正文）。
 * 权限：FileService.getFile 内含归属校验（他人文件 → Forbidden）。
 */
@Component
public class GetFileInfoTool implements Tool {

    private final FileService fileService;

    public GetFileInfoTool(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("getFileInfo", "查询当前用户某个文件的元数据（名称/大小/类型/SHA256）")
                .param("fileId", ToolDefinition.INTEGER, "文件 id（必填）", true)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, User user) {
        Long fileId;
        try {
            fileId = ToolArgs.toLong(arguments.get("fileId"));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("getFileInfo", ToolErrorCode.INVALID_ARGUMENT, e.getMessage(), false);
        }
        try {
            FileResponse file = fileService.getFile(fileId, user);
            return ToolResult.ok("getFileInfo", file);
        } catch (FileRecordNotFoundException e) {
            return ToolResult.error("getFileInfo", ToolErrorCode.NOT_FOUND, "文件不存在: id=" + fileId, false);
        } catch (ForbiddenException e) {
            return ToolResult.error("getFileInfo", ToolErrorCode.FORBIDDEN, "无权访问该文件", false);
        }
    }
}
