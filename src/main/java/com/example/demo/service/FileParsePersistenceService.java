package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.enums.FileParseStatus;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.model.DocumentSegment;
import com.example.demo.model.FileRecord;
import com.example.demo.repository.DocumentSegmentRepository;
import com.example.demo.repository.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 解析状态与结果的短事务持久化 —— 独立 Bean，保证 @Transactional 经代理生效。
 * <p>
 * 每个方法只做一次短事务提交，不把耗时的文档解析过程包进长事务。
 */
@Service
public class FileParsePersistenceService {

    private final FileRepository fileRepository;
    private final DocumentSegmentRepository segmentRepository;

    public FileParsePersistenceService(FileRepository fileRepository,
                                       DocumentSegmentRepository segmentRepository) {
        this.fileRepository = fileRepository;
        this.segmentRepository = segmentRepository;
    }

    /** 提交解析：置为 PENDING（方法返回即提交，供异步线程可见） */
    @Transactional
    public void markPending(Long fileId) {
        FileRecord record = require(fileId);
        record.setParseStatus(FileParseStatus.PENDING);
        record.setParseFailureReason(null);
    }

    /** 解析开始：PENDING → PROCESSING */
    @Transactional
    public void markProcessing(Long fileId) {
        FileRecord record = require(fileId);
        record.setParseStatus(FileParseStatus.PROCESSING);
    }

    /** 解析成功：清理旧片段 + 保存新片段 + PROCESSING → SUCCESS */
    @Transactional
    public void markSuccess(Long fileId, ParsedDocument document) {
        FileRecord record = require(fileId);
        segmentRepository.deleteByFileId(fileId);
        for (ParsedDocument.ParsedSegment s : document.segments()) {
            segmentRepository.save(new DocumentSegment(record, s.type(), s.index(), s.text()));
        }
        record.setParseStatus(FileParseStatus.SUCCESS);
        record.setParseFailureReason(null);
        record.setParsedAt(LocalDateTime.now());
    }

    /** 解析失败：PROCESSING → FAILED，保存简洁失败原因 */
    @Transactional
    public void markFailed(Long fileId, String reason) {
        FileRecord record = require(fileId);
        record.setParseStatus(FileParseStatus.FAILED);
        record.setParseFailureReason(reason);
    }

    private FileRecord require(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
    }
}
