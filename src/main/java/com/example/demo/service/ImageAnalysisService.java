package com.example.demo.service;

import com.example.demo.config.ImageAnalysisProperties;
import com.example.demo.dto.ImageAnalysisResponse;
import com.example.demo.dto.ImageAnalysisStatusResponse;
import com.example.demo.enums.ImageAnalysisStatus;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.ImageAnalysisConflictException;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.ImageAnalysis;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import com.example.demo.repository.ImageAnalysisRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.RejectedExecutionException;

/**
 * 图片分析编排 —— 提交分析任务、查询状态与结果。
 * <p>
 * 原子防并发：首次创建靠唯一约束；FAILED 重试用条件 UPDATE；
 * 线程池拒绝时立即 markFailed，避免永久 PENDING。
 */
@Service
public class ImageAnalysisService {

    private final FileRepository fileRepository;
    private final ImageAnalysisRepository imageAnalysisRepository;
    private final ImageAnalysisPersistenceService persistence;
    private final ImageAnalysisWorker worker;
    private final ImageAnalysisProperties properties;

    public ImageAnalysisService(FileRepository fileRepository,
                                ImageAnalysisRepository imageAnalysisRepository,
                                ImageAnalysisPersistenceService persistence,
                                ImageAnalysisWorker worker,
                                ImageAnalysisProperties properties) {
        this.fileRepository = fileRepository;
        this.imageAnalysisRepository = imageAnalysisRepository;
        this.persistence = persistence;
        this.worker = worker;
        this.properties = properties;
    }

    /** 提交图片分析（首次或 FAILED 重试） */
    public void submitAnalysis(Long fileId, User user) {
        FileRecord record = requireOwned(fileId, user);
        if (!FileContentValidator.isImageExtension(record.getExtension())) {
            throw new InvalidFileException("该文件不是支持的图片类型（jpg/png/webp）");
        }

        // 原子状态转换：无记录→创建 PENDING；有记录→仅 FAILED 可重置为 PENDING
        imageAnalysisRepository.findByFileId(fileId).ifPresentOrElse(
                existing -> {
                    int updated = persistence.resetToPending(fileId);
                    if (updated == 0) {
                        throw new ImageAnalysisConflictException(conflictMessage(existing.getStatus()));
                    }
                },
                () -> persistence.createPending(fileId));

        // 触发异步；线程池拒绝时立即补偿为 FAILED，不留永久 PENDING
        try {
            worker.runAsync(fileId);
        } catch (RejectedExecutionException e) {
            persistence.markFailed(fileId, "图片分析任务提交失败，线程池繁忙");
            throw new ImageAnalysisConflictException("图片分析任务繁忙，请稍后重试");
        }
    }

    /** 查询分析状态 */
    public ImageAnalysisStatusResponse getStatus(Long fileId, User user) {
        requireOwned(fileId, user);
        return imageAnalysisRepository.findByFileId(fileId)
                .map(ImageAnalysisStatusResponse::from)
                .orElseGet(ImageAnalysisStatusResponse::pending);
    }

    /** 查询分析结果（仅 SUCCESS 有结果） */
    public ImageAnalysisResponse getResult(Long fileId, User user) {
        requireOwned(fileId, user);
        ImageAnalysis a = imageAnalysisRepository.findByFileId(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
        if (a.getStatus() != ImageAnalysisStatus.SUCCESS) {
            throw new ImageAnalysisConflictException("图片分析尚未成功完成");
        }
        return ImageAnalysisResponse.from(a);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    private String conflictMessage(ImageAnalysisStatus status) {
        return switch (status) {
            case PROCESSING -> "图片正在分析中，请勿重复提交";
            case SUCCESS -> "图片已分析成功，无需重复分析";
            default -> "图片分析已提交，请勿重复提交";
        };
    }

    private FileRecord requireOwned(Long fileId, User user) {
        FileRecord record = fileRepository.findByIdWithUser(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
        if (!record.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("无权访问该文件");
        }
        return record;
    }
}
