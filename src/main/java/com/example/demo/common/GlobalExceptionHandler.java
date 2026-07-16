package com.example.demo.common;

import com.example.demo.exception.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 —— 拦截所有 Controller 抛出的异常，统一转成 ApiResponse 格式
 * <p>
 * 作用：把难看的 500 + 堆栈信息，变成干净的 JSON 返回
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获"任务不存在"异常 → 返回 404
     */
    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)   // HTTP 404
    public ApiResponse<Void> handleTaskNotFound(TaskNotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    /**
     * 捕获"参数校验失败"异常 → 返回 400
     * 例如：title 为空、inputText 超长等
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)   // HTTP 400
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        // 取第一个校验失败的错误信息
        String msg = e.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();
        return ApiResponse.error(400, msg);
    }

    /**
     * 捕获所有未被上面单独处理的异常 → 返回 500
     * 兜底：防止堆栈信息直接暴露给前端
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)   // HTTP 500
    public ApiResponse<Void> handleOther(Exception e) {
        return ApiResponse.error(500, "服务器内部错误: " + e.getMessage());
    }
}