package com.sm.instagram.platform.storage.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_uploads", indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_upload_time", columnList = "uploadTime"),
        @Index(name = "idx_file_path", columnList = "filePath", unique = true)
})
@Getter
@Setter
public class FileUpload {

    @Id
    @Size(max = 36, message = "ID cannot exceed 36 characters")
    private String id;

    @Column(nullable = false)
    @NotBlank(message = "User ID cannot be blank")
    @Size(max = 255, message = "User ID cannot exceed 255 characters")
    private String userId;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "File path cannot be blank")
    @Size(max = 1000, message = "File path cannot exceed 1000 characters")
    private String filePath;

    @Column(nullable = false)
    @NotBlank(message = "Filename cannot be blank")
    @Size(max = 255, message = "Filename cannot exceed 255 characters")
    private String filename;

    @Column(nullable = false)
    @NotBlank(message = "Content type cannot be blank")
    @Size(max = 100, message = "Content type cannot exceed 100 characters")
    private String contentType;

    @Column(nullable = false)
    @NotNull(message = "File size cannot be null")
    @Positive(message = "File size must be positive")
    private Long fileSize;

    @Column(nullable = false)
    @NotNull(message = "Upload time cannot be null")
    private Instant uploadTime;

    @Size(max = 1000, message = "Public URL cannot exceed 1000 characters")
    private String publicUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Size(max = 500, message = "Alt text cannot exceed 500 characters")
    private String altText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Status cannot be null")
    private UploadStatus status = UploadStatus.PENDING;

    private Instant confirmedAt;

    // Audit fields
    @Column(nullable = false, updatable = false)
    @NotNull(message = "Created at cannot be null")
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    // Constructors
    public FileUpload() {
    }

    public FileUpload(String userId, String filePath, String filename,
                      String contentType, Long fileSize) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.filePath = filePath;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadTime = Instant.now();
    }

    @PrePersist
    protected void onInsert() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Instant uploadTime) {
        this.uploadTime = uploadTime;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public void setStatus(UploadStatus status) {
        this.status = status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public enum UploadStatus {
        PENDING,      // Signed URL generated, upload not confirmed
        CONFIRMED,    // Upload confirmed via API
        WEBHOOK,      // Confirmed via Firebase webhook
        FAILED,       // Upload failed
        DELETED       // File was deleted
    }
}
