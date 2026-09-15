package com.example.demo.exception;

/**
 * 存储服务内部异常（磁盘读写失败、存储路径越界、物理文件缺失）→ HTTP 500
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
