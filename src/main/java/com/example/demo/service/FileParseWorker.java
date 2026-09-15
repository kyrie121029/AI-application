package com.example.demo.service;

import com.example.demo.dto.ParsedDocument;
import com.example.demo.exception.DocumentParseException;
import com.example.demo.model.FileRecord;
import com.example.demo.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 异步解析执行器 —— @Async 方法放独立 Bean，避免 self-invocation 导致注解失效。
 * <p>
 * 执行顺序（解析在事务外，状态更新走 FileParsePersistenceService 短事务）：
 *   markProcessing → load + parse → markSuccess / markFailed。
 * <p>
 * InputStream 生命周期：本类在 try-with-resources 中打开并关闭 storageService.load() 返回的流，
 * Parser 只消费不关闭。
 */
@Service
public class FileParseWorker {

    private static final Logger log = LoggerFactory.getLogger(FileParseWorker.class);

    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final DocumentParserRegistry parserRegistry;
    private final FileParsePersistenceService persistence;
    private final ChunkingService chunkingService;

    public FileParseWorker(FileRepository fileRepository,
                           StorageService storageService,
                           DocumentParserRegistry parserRegistry,
                           FileParsePersistenceService persistence,
                           ChunkingService chunkingService) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.parserRegistry = parserRegistry;
        this.persistence = persistence;
        this.chunkingService = chunkingService;
    }

    @Async("fileParseExecutor")
    public void runAsync(Long fileId) {
        long start = System.currentTimeMillis();
        try {
            persistence.markProcessing(fileId);
            ParsedDocument document = parseDocument(fileId);
            persistence.markSuccess(fileId, document);
            log.info("文件解析成功: fileId={}, segments={}, elapsed={}ms",
                    fileId, document.segments().size(), System.currentTimeMillis() - start);
            // 解析成功后触发分块；分块失败不影响解析 SUCCESS 状态
            try {
                chunkingService.chunkFile(fileId);
            } catch (Exception e) {
                log.error("解析成功后分块失败（不影响解析结果）: fileId={}", fileId, e);
            }
        } catch (Exception e) {
            log.error("文件解析失败: fileId={}, elapsed={}ms",
                    fileId, System.currentTimeMillis() - start, e);
            persistence.markFailed(fileId, safeReason(e));
        }
    }

    /** 读取 + 解析（事务外）；InputStream 由本方法负责关闭 */
    private ParsedDocument parseDocument(Long fileId) {
        FileRecord record = fileRepository.findById(fileId)
                .orElseThrow(() -> new DocumentParseException("文件不存在: id=" + fileId));
        DocumentParser parser = parserRegistry.get(record.getExtension());
        Resource resource = storageService.load(record.getStorageKey());
        try (InputStream in = resource.getInputStream()) {
            return parser.parse(in);
        } catch (IOException e) {
            throw new DocumentParseException("读取文件内容失败", e);
        }
    }

    /** 只保存简洁可读的失败原因，不保存完整堆栈 */
    private String safeReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 400 ? message.substring(0, 400) : message;
    }
}
