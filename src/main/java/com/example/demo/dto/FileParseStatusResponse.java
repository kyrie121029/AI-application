package com.example.demo.dto;

import com.example.demo.enums.FileParseStatus;
import com.example.demo.model.FileRecord;

import java.time.LocalDateTime;

/**
 * 文件解析状态响应。
 */
public class FileParseStatusResponse {

    private FileParseStatus status;
    private String failureReason;
    private LocalDateTime parsedAt;

    private FileParseStatusResponse() {}

    public static FileParseStatusResponse from(FileRecord r) {
        FileParseStatusResponse response = new FileParseStatusResponse();
        response.status = r.getParseStatus();
        response.failureReason = r.getParseFailureReason();
        response.parsedAt = r.getParsedAt();
        return response;
    }

    public FileParseStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getParsedAt() { return parsedAt; }
}
