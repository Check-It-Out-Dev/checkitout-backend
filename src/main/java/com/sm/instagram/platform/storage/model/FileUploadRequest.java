package com.sm.instagram.platform.storage.model;

import jakarta.validation.constraints.*;

public class FileUploadRequest {

    @NotBlank(message = "{validation.file.filename.required}")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
            message = "{validation.file.filename.pattern}")
    @Size(max = 255, message = "{validation.file.filename.size}")
    private String filename;

    @NotBlank(message = "{validation.file.contentType.required}")
    @Pattern(regexp = "^image/(jpeg|jpg|png|gif|webp)$",
            message = "{validation.file.contentType.imagesOnly}")
    // allowableValues mirrors the @Pattern so the generated client exposes a
    // typed contentType enum (FileUploadRequestContentTypeEnum) instead of a
    // bare string — docs-only, runtime validation stays the regex above.
    @io.swagger.v3.oas.annotations.media.Schema(
            allowableValues = {"image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"})
    private String contentType;

    @NotNull(message = "{validation.file.fileSize.required}")
    @Min(value = 1, message = "{validation.file.fileSize.min}")
    @Max(value = 5242880, message = "{validation.file.fileSize.max}") // 5MB
    // This will be validated in service layer against config
    private Long fileSize;

    // Optional metadata
    private String description;
    private String altText;  // For accessibility

    /**
     * Upload type for differentiated rate limiting.
     * Valid values: "PROFILE_PHOTO", "CONTENT", "CAMPAIGN_MEDIA"
     * Defaults to "CONTENT" if not specified or null.
     * PROFILE_PHOTO: Exempt from hourly/daily limits (ensures new users can upload profile pics)
     * CONTENT: Standard content uploads with full rate limiting
     * CAMPAIGN_MEDIA: Part of campaign creation flow, uses campaign-specific limits (2x normal)
     */
    @Pattern(regexp = "^(PROFILE_PHOTO|CONTENT|CAMPAIGN_MEDIA)?$",
            message = "{validation.file.uploadType.pattern}")
    private String uploadType;

    // Getters and setters
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

    public String getUploadType() {
        return uploadType;
    }

    public void setUploadType(String uploadType) {
        this.uploadType = uploadType;
    }

    /**
     * Extracts file extension from content type.
     * For example: "image/jpeg" -> "jpg"
     */
    public String getFileExtension() {
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported content type: " + contentType);
        };
    }
}
