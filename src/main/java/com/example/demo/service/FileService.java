package com.example.demo.service;

import com.example.demo.config.StorageProperties;
import com.example.demo.dto.FileDownload;
import com.example.demo.dto.FileResponse;
import com.example.demo.exception.FileRecordNotFoundException;
import com.example.demo.exception.FileStorageException;
import com.example.demo.exception.FileTooLargeException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.model.FileRecord;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import com.example.demo.util.ImageDimensionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 文件服务 —— 上传 / 查询 / 下载 / 删除的完整业务编排。
 * <p>
 * 只依赖 StorageService 抽象（不依赖 LocalStorageService），为后续 MinIO 替换保留能力。
 * <p>
 * 上传顺序：校验 → SHA-256（流式）→ 生成 storageKey → 磁盘写入 → 数据库入库；
 * 入库失败时补偿删除物理文件，避免孤儿文件。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final FileContentValidator contentValidator;
    private final StorageProperties storageProperties;
    private final FileParseService fileParseService;
    private final ImageAnalysisService imageAnalysisService;
    private final DocumentIndexService documentIndexService;

    public FileService(FileRepository fileRepository,
                       StorageService storageService,
                       FileContentValidator contentValidator,
                       StorageProperties storageProperties,
                       FileParseService fileParseService,
                       ImageAnalysisService imageAnalysisService,
                       DocumentIndexService documentIndexService) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.contentValidator = contentValidator;
        this.storageProperties = storageProperties;
        this.fileParseService = fileParseService;
        this.imageAnalysisService = imageAnalysisService;
        this.documentIndexService = documentIndexService;
    }

    /** 上传文件：校验 → SHA-256 → 生成 storageKey → 磁盘写入 → 入库（失败补偿删除） */
    public FileResponse upload(MultipartFile file, User user) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("文件内容为空");
        }
        if (file.getSize() > storageProperties.getMaxFileSize().toBytes()) {
            throw new FileTooLargeException("文件大小超过限制（最大 "
                    + storageProperties.getMaxFileSize().toMegabytes() + "MB）");
        }

        String extension = contentValidator.validate(file);
        String sha256 = computeSha256(file);
        String storageKey = buildStorageKey(user.getId(), extension);
        String mimeType = FileContentValidator.mimeTypeOf(extension);

        // 先写磁盘（在事务之外，避免长事务）
        try {
            storageService.store(storageKey, file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new FileStorageException("读取上传文件失败", e);
        }

        // 再写元数据；入库失败 → 补偿删除已写入的物理文件
        FileRecord record = new FileRecord(user, file.getOriginalFilename(), storageKey,
                extension, mimeType, file.getSize(), sha256);
        // 图片提取宽高（仅图片文件；失败不阻断上传）
        if (FileContentValidator.isImageExtension(extension)) {
            try (InputStream in = file.getInputStream()) {
                int[] dims = ImageDimensionReader.read(in);
                if (dims != null) {
                    record.setImageWidth(dims[0]);
                    record.setImageHeight(dims[1]);
                }
            } catch (IOException e) {
                log.warn("图片尺寸读取失败: {}", e.getMessage());
            }
        }
        FileRecord saved;
        try {
            saved = fileRepository.save(record);
        } catch (Exception e) {
            log.error("FileRecord 保存失败，补偿删除物理文件: key={}", storageKey, e);
            try {
                storageService.delete(storageKey);
            } catch (Exception ex) {
                log.error("补偿删除物理文件失败: key={}", storageKey, ex);
            }
            throw e;
        }

        // 上传成功后自动提交异步任务（图片→图片分析，文档→文本解析）；提交失败不影响上传结果
        try {
            if (FileContentValidator.isImageExtension(extension)) {
                if (imageAnalysisService.isEnabled()) {
                    imageAnalysisService.submitAnalysis(saved.getId(), user);
                }
            } else {
                fileParseService.submitParse(saved.getId(), user);
            }
        } catch (Exception e) {
            log.error("自动提交分析任务失败: fileId={}", saved.getId(), e);
        }
        return FileResponse.from(saved);
    }

    /** 当前用户的文件列表（时间倒序） */
    public List<FileResponse> listFiles(User user) {
        return fileRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(FileResponse::from).toList();
    }

    /** 查询单个文件元数据（归属校验） */
    public FileResponse getFile(Long fileId, User user) {
        return FileResponse.from(findOwned(fileId, user));
    }

    /** 下载文件（归属校验；返回 Resource 由 Controller 组装 HTTP 响应） */
    public FileDownload downloadFile(Long fileId, User user) {
        FileRecord record = findOwned(fileId, user);
        Resource resource = storageService.load(record.getStorageKey());
        return new FileDownload(resource, record.getOriginalFilename(), record.getMimeType());
    }

    /**
     * 删除文件。
     * 顺序：先删数据库记录（避免出现"记录在但文件丢"的悬空状态），
     * 再删物理文件与派生向量；物理删除失败仅记日志（孤儿文件无害，可后续清理）。
     */
    public void deleteFile(Long fileId, User user) {
        FileRecord record = findOwned(fileId, user);
        fileRepository.delete(record);
        try {
            storageService.delete(record.getStorageKey());
        } catch (Exception e) {
            log.error("物理文件删除失败（数据库记录已删除）: key={}", record.getStorageKey(), e);
        }
        // 清理派生向量索引（best-effort，不可达不影响删除）
        documentIndexService.deleteVectorIndex(fileId);
    }

    // ==================== 私有方法 ====================

    /** 归属校验：不存在 → 404；不是所有者 → 403 */
    private FileRecord findOwned(Long fileId, User user) {
        FileRecord record = fileRepository.findByIdWithUser(fileId)
                .orElseThrow(() -> new FileRecordNotFoundException(fileId));
        if (!record.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("无权访问该文件");
        }
        return record;
    }

    /** SHA-256 流式计算（不把整个文件读入内存） */
    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        } catch (IOException e) {
            throw new FileStorageException("读取上传文件失败", e);
        }
    }

    /** 系统生成的内部定位标识：{userId}/{yyyy}/{MM}/{uuid}.{ext}，绝不使用原始文件名 */
    private String buildStorageKey(Long userId, String extension) {
        LocalDate now = LocalDate.now();
        return userId + "/" + now.getYear() + "/"
                + String.format("%02d", now.getMonthValue()) + "/"
                + UUID.randomUUID() + "." + extension;
    }
}
