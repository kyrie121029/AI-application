package com.example.demo.common;

import com.example.demo.exception.AIResponseParseException;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.ConversationNotFoundException;
import com.example.demo.exception.DuplicateRequestException;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.FileStorageException;
import com.example.demo.exception.FileTooLargeException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.ImageAnalysisConflictException;
import com.example.demo.exception.IndexStatusConflictException;
import com.example.demo.exception.InputTooLongException;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.exception.ParseConflictException;
import com.example.demo.exception.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
     * 捕获"会话不存在"异常 → 返回 404
     */
    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)   // HTTP 404
    public ApiResponse<Void> handleConversationNotFound(ConversationNotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    /**
     * 捕获"重复请求"异常 → 返回 409
     */
    @ExceptionHandler(DuplicateRequestException.class)
    @ResponseStatus(HttpStatus.CONFLICT)   // HTTP 409
    public ApiResponse<Void> handleDuplicateRequest(DuplicateRequestException e) {
        return ApiResponse.error(409, e.getMessage());
    }

    /**
     * 捕获"当前消息超出 Token 上下文预算"异常 → 返回 413
     */
    @ExceptionHandler(InputTooLongException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)   // HTTP 413
    public ApiResponse<Void> handleInputTooLong(InputTooLongException e) {
        return ApiResponse.error(413, e.getMessage());
    }

    /**
     * 捕获"权限不足"异常 → 返回 403
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)   // HTTP 403
    public ApiResponse<Void> handleForbidden(ForbiddenException e) {
        return ApiResponse.error(403, e.getMessage());
    }

    /**
     * 捕获"非法文件"异常 → 返回 400
     */
    @ExceptionHandler(InvalidFileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)   // HTTP 400
    public ApiResponse<Void> handleInvalidFile(InvalidFileException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    /**
     * 捕获"文件不存在"异常 → 返回 404
     */
    @ExceptionHandler(FileRecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)   // HTTP 404
    public ApiResponse<Void> handleFileNotFound(FileRecordNotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    /**
     * 捕获"解析状态冲突"异常 → 返回 409（PROCESSING 重复提交 / SUCCESS 重复解析）
     */
    @ExceptionHandler(ParseConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)   // HTTP 409
    public ApiResponse<Void> handleParseConflict(ParseConflictException e) {
        return ApiResponse.error(409, e.getMessage());
    }

    /**
     * 捕获"索引状态冲突"异常 → 返回 409
     */
    @ExceptionHandler(IndexStatusConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)   // HTTP 409
    public ApiResponse<Void> handleIndexStatusConflict(IndexStatusConflictException e) {
        return ApiResponse.error(409, e.getMessage());
    }

    /**
     * 捕获"图片分析状态冲突"异常 → 返回 409
     */
    @ExceptionHandler(ImageAnalysisConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)   // HTTP 409
    public ApiResponse<Void> handleImageAnalysisConflict(ImageAnalysisConflictException e) {
        return ApiResponse.error(409, e.getMessage());
    }

    /**
     * 捕获"文件大小超过业务层限制"异常 → 返回 413
     */
    @ExceptionHandler(FileTooLargeException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)   // HTTP 413
    public ApiResponse<Void> handleFileTooLarge(FileTooLargeException e) {
        return ApiResponse.error(413, e.getMessage());
    }

    /**
     * 捕获 Spring multipart 层的大小上限异常 → 返回 413（与业务层 FileTooLargeException 一致）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)   // HTTP 413
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ApiResponse.error(413, "文件大小超过上传限制");
    }

    /**
     * 捕获"存储服务内部异常" → 返回 500
     */
    @ExceptionHandler(FileStorageException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)   // HTTP 500
    public ApiResponse<Void> handleFileStorage(FileStorageException e) {
        return ApiResponse.error(500, "文件存储异常: " + e.getMessage());
    }

    /**
     * 捕获"AI 服务调用失败"异常 → 返回 502
     * 区分 errorType：auth(不重试) / rate_limit(重试) / timeout(重试) / network(重试) / server(重试)
     */
    @ExceptionHandler(AIServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)   // HTTP 502
    public ApiResponse<Void> handleAIService(AIServiceException e) {
        return ApiResponse.error(502, "AI 服务异常[" + e.getErrorType() + "]: " + e.getMessage());
    }

    /**
     * 捕获"AI 响应解析失败"异常 → 返回 502
     * 上游 AI 返回了内容但格式不符合预期
     */
    @ExceptionHandler(AIResponseParseException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)   // HTTP 502
    public ApiResponse<Void> handleAIResponseParse(AIResponseParseException e) {
        return ApiResponse.error(502, "AI 响应解析失败: " + e.getMessage());
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