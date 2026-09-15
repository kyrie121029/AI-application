package com.example.demo.exception;

/**
 * 文件记录不存在 → HTTP 404
 */
public class FileRecordNotFoundException extends RuntimeException {

    public FileRecordNotFoundException(Long fileId) {
        super("文件不存在: id=" + fileId);
    }
}
