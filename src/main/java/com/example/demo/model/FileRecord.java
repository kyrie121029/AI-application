package com.example.demo.model;

import com.example.demo.enums.FileParseStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 文件元数据记录 —— 只保存元数据，文件二进制存本地文件系统（不存 MySQL BLOB）
 * <p>
 * storageKey：系统生成的内部定位标识（如 12/2026/08/uuid.pdf），
 * originalFilename：仅用于用户展示，绝不作为磁盘路径。
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "file_records")
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户（多对一） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 用户上传时的原始文件名（仅展示用） */
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /** 系统生成的内部存储定位标识 */
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    /** 规范化扩展名（小写，如 pdf） */
    @Column(name = "extension", length = 16)
    private String extension;

    /** 规范化 MIME 类型（由扩展名 allowlist 映射，不信任客户端声明） */
    @Column(name = "mime_type", length = 128)
    private String mimeType;

    /** 文件大小（字节） */
    @Column(name = "size_bytes", nullable = false)
    private long size;

    /** SHA-256 内容指纹（64 位十六进制） */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 解析状态（PENDING / PROCESSING / SUCCESS / FAILED） */
    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", length = 20)
    private FileParseStatus parseStatus = FileParseStatus.PENDING;

    /** 解析失败原因（简洁可读，不含堆栈） */
    @Column(name = "parse_failure_reason", length = 500)
    private String parseFailureReason;

    /** 最近一次解析成功时间 */
    @Column(name = "parsed_at")
    private LocalDateTime parsedAt;

    /** 图片宽度（仅图片文件，可能为 null） */
    @Column(name = "image_width")
    private Integer imageWidth;

    /** 图片高度（仅图片文件，可能为 null） */
    @Column(name = "image_height")
    private Integer imageHeight;

    // ==================== 构造方法 ====================

    public FileRecord() {}

    public FileRecord(User user, String originalFilename, String storageKey,
                      String extension, String mimeType, long size, String sha256) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.extension = extension;
        this.mimeType = mimeType;
        this.size = size;
        this.sha256 = sha256;
        this.createdAt = LocalDateTime.now();
    }

    // ==================== Getter / Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public FileParseStatus getParseStatus() { return parseStatus; }
    public void setParseStatus(FileParseStatus parseStatus) { this.parseStatus = parseStatus; }
    public String getParseFailureReason() { return parseFailureReason; }
    public void setParseFailureReason(String parseFailureReason) { this.parseFailureReason = parseFailureReason; }
    public LocalDateTime getParsedAt() { return parsedAt; }
    public void setParsedAt(LocalDateTime parsedAt) { this.parsedAt = parsedAt; }
    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }
    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }
}
