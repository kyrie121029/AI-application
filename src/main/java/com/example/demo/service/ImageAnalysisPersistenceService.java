package com.example.demo.service;

import com.example.demo.dto.ImageAnalysisResult;
import com.example.demo.enums.ImageAnalysisStatus;
import com.example.demo.exception.ImageAnalysisConflictException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.ImageAnalysis;
import com.example.demo.repository.FileRepository;
import com.example.demo.repository.ImageAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图片分析状态与结果的短事务持久化。
 * <p>
 * 状态转换用原子条件 UPDATE；首次创建靠 file_id 唯一约束兜底并发。
 */
@Service
public class ImageAnalysisPersistenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ImageAnalysisRepository imageAnalysisRepository;
    private final FileRepository fileRepository;

    public ImageAnalysisPersistenceService(ImageAnalysisRepository imageAnalysisRepository,
                                           FileRepository fileRepository) {
        this.imageAnalysisRepository = imageAnalysisRepository;
        this.fileRepository = fileRepository;
    }

    /** 首次创建 PENDING 记录；并发撞唯一约束 → ImageAnalysisConflictException */
    @Transactional
    public void createPending(Long fileId) {
        FileRecord file = fileRepository.findById(fileId)
                .orElseThrow(() -> new com.example.demo.exception.FileRecordNotFoundException(fileId));
        try {
            // saveAndFlush 确保唯一约束冲突在 try 内立即触发，而不是事务提交时才抛出
            imageAnalysisRepository.saveAndFlush(new ImageAnalysis(file));
        } catch (DataIntegrityViolationException e) {
            throw new ImageAnalysisConflictException("图片分析已提交，请勿重复提交");
        }
    }

    /** FAILED → PENDING 原子重试；返回影响行数，0 表示状态冲突 */
    @Transactional
    public int resetToPending(Long fileId) {
        return imageAnalysisRepository.resetToPending(fileId);
    }

    /** PENDING → PROCESSING；返回影响行数，0 表示状态已被抢占（调用方应中止） */
    @Transactional
    public int markProcessing(Long fileId) {
        return imageAnalysisRepository.markProcessing(fileId);
    }

    /** 保存结果 + SUCCESS */
    @Transactional
    public void markSuccess(Long fileId, ImageAnalysisResult result, String modelName) {
        ImageAnalysis a = require(fileId);
        a.setStatus(ImageAnalysisStatus.SUCCESS);
        a.setModelName(modelName);
        a.setSummary(result.summary());
        a.setScene(result.scene());
        a.setTextContent(result.textContent());
        a.setObjectsJson(toJson(result.objects()));
        a.setRiskFlagsJson(toJson(result.riskFlags()));
        a.setFailureReason(null);
        a.setAnalyzedAt(LocalDateTime.now());
    }

    /** FAILED + 简洁失败原因 */
    @Transactional
    public void markFailed(Long fileId, String reason) {
        ImageAnalysis a = require(fileId);
        a.setStatus(ImageAnalysisStatus.FAILED);
        a.setFailureReason(reason);
    }

    private ImageAnalysis require(Long fileId) {
        return imageAnalysisRepository.findByFileId(fileId)
                .orElseThrow(() -> new com.example.demo.exception.FileRecordNotFoundException(fileId));
    }

    private String toJson(List<String> list) {
        try {
            return MAPPER.writeValueAsString(list == null ? java.util.List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
