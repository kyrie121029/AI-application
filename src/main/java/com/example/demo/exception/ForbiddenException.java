package com.example.demo.exception;

/**
 * 权限不足异常 —— 用户试图操作别人的资源
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}