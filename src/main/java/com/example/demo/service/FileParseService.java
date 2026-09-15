package com.example.demo.service;

import com.example.demo.dto.FileParseStatusResponse;
import com.example.demo.enums.FileParseStatus;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.ParseConflictException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import org.springframework.stereotype.Service;

/**
 * 解析编排 —— 提交解析任务与查询解析状态。
 * <p>
 * 状态机：PENDING → PROCESSING → SUCCESS / FAILED（FAILED 可重试）。
 * 防重复：PROCESSING 禁止重复提交，SUCCESS 默认不重复解析。
 */
@Service
public class FileParseService {

    private final FileRepository fileRepository;
    private final FileParsePersistenceService persistence;
    private final FileParseWorker worker;

    public FileParseService(FileRepository fileRepository,
                            FileParsePersistenceService persistence,
                            FileParseWorker worker) {
        this.fileRepository = fileRepository;
        this.persistence = persistence;
        this.worker = worker;
    }

    /** 提交解析（首次或 FAILED 重试）；状态校验通过后置 PENDING 并触发异步 */
    public void submitParse(Long fileId, User user) {
        FileRecord record = requireOwned(fileId, user);
        FileParseStatus status = record.getParseStatus();
        if (status == FileParseStatus.PROCESSING) {
            throw new ParseConflictException("文件正在解析中，请勿重复提交");
        }
        if (status == FileParseStatus.SUCCESS) {
            throw new ParseConflictException("文件已解析成功，无需重复解析");
        }
        // PENDING 或 FAILED 允许
        persistence.markPending(fileId);   // 短事务，返回即提交
        worker.runAsync(fileId);           // 注入 Bean 调用，@Async 生效
    }

    /** 查询解析状态（归属校验） */
    public FileParseStatusResponse getParseStatus(Long fileId, User user) {
        return FileParseStatusResponse.from(requireOwned(fileId, user));
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
