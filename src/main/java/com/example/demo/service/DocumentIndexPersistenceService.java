package com.example.demo.service;

import com.example.demo.enums.IndexStatus;
import com.example.demo.exception.IndexStatusConflictException;
import com.example.demo.model.DocumentIndex;
import com.example.demo.repository.DocumentIndexRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 文档索引状态短事务持久化 —— 独立 Bean，保证 @Transactional 经代理生效。
 * 每个方法只做一次短事务提交，不把 Embedding + Qdrant 网络调用包进事务。
 * <p>
 * 并发防护（beginIndex，一个短事务内）：
 *   1) createIfAbsent：数据库原生原子 upsert 建 PENDING 行（H2 用 MERGE、MySQL 用
 *      INSERT ... ON DUPLICATE KEY UPDATE），不用"saveAndFlush + catch 唯一约束异常"
 *       —— 避免异常把事务置为 rollback-only；
 *   2) tryMarkIndexing：CAS（非 INDEXING → INDEXING），影响行数 0 = 并发请求正 INDEXING → 冲突。
 */
@Service
public class DocumentIndexPersistenceService {

    private static final String CREATE_IF_ABSENT_H2 =
            "MERGE INTO document_indexes (file_id, status, created_at) KEY(file_id) "
                    + "VALUES (?, 'PENDING', CURRENT_TIMESTAMP)";
    private static final String CREATE_IF_ABSENT_MYSQL =
            "INSERT INTO document_indexes (file_id, status, created_at) VALUES (?, 'PENDING', CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE file_id = file_id";

    private final DocumentIndexRepository indexRepository;
    private final JdbcTemplate jdbcTemplate;

    public DocumentIndexPersistenceService(DocumentIndexRepository indexRepository,
                                           JdbcTemplate jdbcTemplate) {
        this.indexRepository = indexRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 原子抢占 INDEXING：createIfAbsent(PENDING) → CAS(INDEXING)。
     * CAS 影响行数 0 = 另一请求正 INDEXING → 拒绝。
     */
    @Transactional
    public void beginIndex(Long fileId, String model, int dimension, String indexVersion) {
        createIfAbsent(fileId);
        int claimed = indexRepository.tryMarkIndexing(fileId, model, dimension, indexVersion);
        if (claimed == 0) {
            throw new IndexStatusConflictException("文件正在建立索引中，请勿重复提交");
        }
    }

    /** 索引成功 */
    @Transactional
    public void markSuccess(Long fileId) {
        DocumentIndex index = require(fileId);
        index.setStatus(IndexStatus.SUCCESS);
        index.setErrorMessage(null);
        index.setUpdatedAt(LocalDateTime.now());
    }

    /** 索引失败（简洁原因） */
    @Transactional
    public void markFailed(Long fileId, String errorMessage) {
        DocumentIndex index = require(fileId);
        index.setStatus(IndexStatus.FAILED);
        index.setErrorMessage(errorMessage);
        index.setUpdatedAt(LocalDateTime.now());
    }

    /** 数据库原子 createIfAbsent（按方言选择语句，无异常流控） */
    private void createIfAbsent(Long fileId) {
        String sql = isH2()
                ? CREATE_IF_ABSENT_H2
                : CREATE_IF_ABSENT_MYSQL;
        jdbcTemplate.update(sql, fileId);
    }

    private boolean isH2() {
        return databaseProduct().toLowerCase().contains("h2");
    }

    /** 数据库产品名（可被子类 override 以在单测中固定方言） */
    protected String databaseProduct() {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException("无法识别数据库类型", e);
        }
    }

    private DocumentIndex require(Long fileId) {
        return indexRepository.findByFileId(fileId)
                .orElseThrow(() -> new com.example.demo.exception.FileRecordNotFoundException(fileId));
    }
}
