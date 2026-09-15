package com.example.demo.service;

import com.example.demo.config.ImageAnalysisProperties;
import com.example.demo.dto.ImageAnalysisResult;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.FileStorageException;
import com.example.demo.exception.MultimodalAIException;
import com.example.demo.model.AIUsageLog;
import com.example.demo.model.FileRecord;
import com.example.demo.repository.AIUsageLogRepository;
import com.example.demo.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 图片分析异步执行器 —— @Async 方法放独立 Bean，避免 self-invocation。
 * <p>
 * InputStream 生命周期：本类在 try-with-resources 中打开并关闭，MultimodalAIService 只消费 byte[]。
 */
@Service
public class ImageAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisWorker.class);

    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final MultimodalAIService multimodalAIService;
    private final ImageAnalysisPersistenceService persistence;
    private final ImageAnalysisProperties properties;
    private final AIUsageLogRepository usageLogRepository;

    public ImageAnalysisWorker(FileRepository fileRepository,
                               StorageService storageService,
                               MultimodalAIService multimodalAIService,
                               ImageAnalysisPersistenceService persistence,
                               ImageAnalysisProperties properties,
                               AIUsageLogRepository usageLogRepository) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.multimodalAIService = multimodalAIService;
        this.persistence = persistence;
        this.properties = properties;
        this.usageLogRepository = usageLogRepository;
    }

    @Async("imageAnalysisExecutor")
    public void runAsync(Long fileId) {
        long start = System.currentTimeMillis();
        try {
            // 检查 affected rows：0 表示状态已被抢占（非 PENDING），中止分析
            if (persistence.markProcessing(fileId) == 0) {
                log.warn("图片分析状态已变化，跳过: fileId={}", fileId);
                return;
            }
            FileRecord record = require(fileId);
            ImageAnalysisResult result = analyzeImage(record);
            persistence.markSuccess(fileId, result, multimodalAIService.getModelName());
            log.info("图片分析成功: fileId={}, model={}, elapsed={}ms",
                    fileId, multimodalAIService.getModelName(), System.currentTimeMillis() - start);
            // 日志保存失败不得影响已成功的结果（内部 try-catch 吞掉）
            saveUsageLog(record, start, true, null);
        } catch (Exception e) {
            log.error("图片分析失败: fileId={}, elapsed={}ms",
                    fileId, System.currentTimeMillis() - start, e);
            persistence.markFailed(fileId, safeReason(e));
            saveFailureLog(fileId, start, e);
        }
    }

    /** 读取图片 + 调用多模态模型（事务外）；InputStream 由本方法负责关闭 */
    private ImageAnalysisResult analyzeImage(FileRecord record) {
        Resource resource = storageService.load(record.getStorageKey());
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            return multimodalAIService.analyzeImage(bytes, record.getMimeType(), properties.getPrompt());
        } catch (IOException e) {
            throw new MultimodalAIException("读取图片内容失败", e);
        }
    }

    private FileRecord require(Long fileId) {
        // JOIN FETCH user，避免事务外访问 record.getUser() 触发 LazyInitializationException
        return fileRepository.findByIdWithUser(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
    }

    /** 保存调用日志；失败只记 warn，不影响主流程（成功/失败结果不受日志影响） */
    private void saveUsageLog(FileRecord record, long start, boolean success, String errorType) {
        try {
            AIUsageLog logEntry = new AIUsageLog();
            logEntry.setUserId(record.getUser() != null ? record.getUser().getId() : null);
            logEntry.setFileId(record.getId());
            logEntry.setProvider(multimodalAIService.getProvider());
            logEntry.setModelName(multimodalAIService.getModelName());
            logEntry.setElapsedMs(System.currentTimeMillis() - start);
            logEntry.setSuccess(success);
            logEntry.setErrorType(errorType);
            usageLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("记录 AI 调用日志失败（不影响主流程）: fileId={}", record.getId(), e);
        }
    }

    /** 失败日志：按异常分类 errorType；若记录本身也拿不到（文件已删）则只记 warn */
    private void saveFailureLog(Long fileId, long start, Exception cause) {
        try {
            saveUsageLog(require(fileId), start, false, classifyError(cause));
        } catch (Exception e) {
            log.warn("记录失败日志失败: fileId={}", fileId, e);
        }
    }

    /** errorType 分类：供 AIUsageLog 统计用，区别于存库的简洁 failureReason */
    private String classifyError(Exception e) {
        if (e instanceof FileStorageException) return "storage";
        if (e instanceof MultimodalAIException) return "model";
        if (e instanceof FileRecordNotFoundException) return "not_found";
        return "internal";
    }

    private String safeReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 400 ? message.substring(0, 400) : message;
    }
}
