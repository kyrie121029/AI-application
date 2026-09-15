package com.example.demo.dto;

import com.example.demo.model.FileRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文件元数据响应 —— 对前端的安全视图。
 * 不返回 storageKey 和服务器绝对路径。
 */
@Schema(description = "文件元数据响应")
public class FileResponse {

    @Schema(description = "文件ID") private Long id;
    @Schema(description = "原始文件名") private String originalFilename;
    @Schema(description = "MIME 类型") private String mimeType;
    @Schema(description = "文件大小（字节）") private long size;
    @Schema(description = "SHA-256 内容指纹") private String sha256;
    @Schema(description = "上传时间") private LocalDateTime createdAt;

    private FileResponse() {}

    public static FileResponse from(FileRecord r) {
        FileResponse response = new FileResponse();
        response.id = r.getId();
        response.originalFilename = r.getOriginalFilename();
        response.mimeType = r.getMimeType();
        response.size = r.getSize();
        response.sha256 = r.getSha256();
        response.createdAt = r.getCreatedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public long getSize() { return size; }
    public String getSha256() { return sha256; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
