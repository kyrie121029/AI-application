package com.example.demo.model;

import com.example.demo.enums.ImageAnalysisStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 图片分析 —— 一个 FileRecord 对应一条（file_id 唯一），承载状态与结构化结果。
 * <p>
 * objects / riskFlags 以 JSON 字符串存储（避免 @ElementCollection 关联表膨胀）。
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "image_analyses")
public class ImageAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private FileRecord file;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImageAnalysisStatus status = ImageAnalysisStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String scene;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "objects_json", columnDefinition = "TEXT")
    private String objectsJson;

    @Column(name = "risk_flags_json", columnDefinition = "TEXT")
    private String riskFlagsJson;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ImageAnalysis() {}

    public ImageAnalysis(FileRecord file) {
        this.file = file;
        this.status = ImageAnalysisStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FileRecord getFile() { return file; }
    public void setFile(FileRecord file) { this.file = file; }
    public ImageAnalysisStatus getStatus() { return status; }
    public void setStatus(ImageAnalysisStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    public String getObjectsJson() { return objectsJson; }
    public void setObjectsJson(String objectsJson) { this.objectsJson = objectsJson; }
    public String getRiskFlagsJson() { return riskFlagsJson; }
    public void setRiskFlagsJson(String riskFlagsJson) { this.riskFlagsJson = riskFlagsJson; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
